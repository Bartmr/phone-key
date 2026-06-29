import { type ReactNode, useEffect, useState } from 'react';
import { View } from 'react-native';
import { ActivityIndicator, Text, useTheme } from 'react-native-paper';

import SshKeyModule from '@/modules/main/src/SshKeyModule';
import { useSpacing } from '@/ui/use-spacing';

export function InitializeSshKey(props: { children: ReactNode }) {
  const theme = useTheme();
  const spacing = useSpacing();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    (async () => {
      const key = await SshKeyModule.getKey();
      if (!key) {
        await SshKeyModule.generateKeyPair();
      }
      setReady(true);
    })();
  }, []);

  if (ready) {
    return props.children;
  }

  return (
    <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', gap: spacing.dp8 }}>
      <ActivityIndicator size="large" color={theme.colors.primary} />
    </View>
  );
}
