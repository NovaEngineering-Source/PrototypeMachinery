import {AppShell, Button, Group, Text} from '@mantine/core';
import {Link, Route, Routes, useLocation} from 'react-router-dom';
import {pmLogoUrl} from '../editor/assets/pmAssets';
import {EditorPage} from './EditorPage';
import {MachineUiPage} from './MachineUiPage';

export function App() {
  const loc = useLocation();
  const path = loc.pathname || '/';
  return (
    <AppShell
      header={{ height: 48 }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group gap="xs">
            <img
              src={pmLogoUrl()}
              alt="PrototypeMachinery"
              onError={(e) => {
                e.currentTarget.style.display = 'none';
              }}
              style={{
                width: 20,
                height: 20,
                objectFit: 'contain',
              }}
            />
            <Text fw={700}>PrototypeMachinery</Text>
            <Text c="dimmed">Web Editor (Draft)</Text>
          </Group>
          <Group gap="xs">
            <Button
              component={Link}
              to="/"
              size="xs"
              variant={path === '/' ? 'light' : 'subtle'}
            >
              JEI Layout
            </Button>
            <Button
              component={Link}
              to="/machine-ui"
              size="xs"
              variant={path.startsWith('/machine-ui') ? 'light' : 'subtle'}
            >
              Machine UI
            </Button>
            <Text size="sm" c="dimmed">Vite + React + Mantine + Konva</Text>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Main
        style={{
          // Prevent the whole page from scrolling; editor panels should manage their own scroll.
          height: 'calc(100vh - 48px)',
          overflow: 'hidden',
          minHeight: 0,
        }}
      >
        <Routes>
          <Route path="/" element={<EditorPage />} />
          <Route path="/machine-ui" element={<MachineUiPage />} />
        </Routes>
      </AppShell.Main>
    </AppShell>
  );
}
