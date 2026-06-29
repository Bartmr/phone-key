import { DefaultTheme, ThemeProvider } from 'expo-router';
import { useColorScheme } from 'react-native';
import { Stack } from 'expo-router';
import { 
  MD3DarkTheme,
  MD3LightTheme,
  MD3Theme,
  PaperProvider,
  useTheme
} from "react-native-paper";
import {
  argbFromHex,
  hexFromArgb,
  themeFromSourceColor,
} from "@material/material-color-utilities";
import { ReactNode, useMemo } from 'react';
import { InitializeSshKey } from './initialize-ssh-key';


function AppPaperProvider(props: { children: ReactNode }) {
    const scheme = useColorScheme();

    /*
    https://callstack.github.io/react-native-paper/docs/guides/theming#creating-dynamic-theme-colors
    */
    const paperTheme = useMemo((): MD3Theme => {
      const baseTheme = themeFromSourceColor(argbFromHex("#4A95EB"));
      
      const md3Theme = scheme === "dark" ? MD3DarkTheme : MD3LightTheme;
      
      const baseThemeScheme =
        scheme === "dark" ? baseTheme.schemes.dark : baseTheme.schemes.light;
      
      return {
        ...md3Theme,
        colors: {
          ...md3Theme.colors,
          primary: hexFromArgb(baseThemeScheme.primary),
          onPrimary: hexFromArgb(baseThemeScheme.onPrimary),
          primaryContainer: hexFromArgb(baseThemeScheme.primaryContainer),
          onPrimaryContainer: hexFromArgb(baseThemeScheme.onPrimaryContainer),
          //
          secondary: hexFromArgb(baseThemeScheme.secondary),
          onSecondary: hexFromArgb(baseThemeScheme.onSecondary),
          secondaryContainer: hexFromArgb(baseThemeScheme.secondaryContainer),
          onSecondaryContainer: hexFromArgb(baseThemeScheme.onSecondaryContainer),
          //
          tertiary: hexFromArgb(baseThemeScheme.tertiary),
          onTertiary: hexFromArgb(baseThemeScheme.onTertiary),
          tertiaryContainer: hexFromArgb(baseThemeScheme.tertiaryContainer),
          onTertiaryContainer: hexFromArgb(baseThemeScheme.onTertiaryContainer),
          //
          surfaceVariant: hexFromArgb(baseThemeScheme.surfaceVariant),
          onSurfaceVariant: hexFromArgb(baseThemeScheme.onSurfaceVariant),
          //
        },
      };
    }, [scheme]);
    
    return <PaperProvider theme={paperTheme}>{props.children}</PaperProvider>
}

export function ExpoRouterThemeProvider(props: {
  children: React.ReactNode;
}) {
  const theme = useTheme();

  return (
    <ThemeProvider
      value={{
        dark: theme.dark,
        colors: {
          primary: theme.colors.primary,
          background: theme.colors.background,
          card: theme.colors.surface,
          text: theme.colors.onBackground,
          border: theme.colors.outline,
          notification: theme.colors.primary,
        },
        fonts: {
          regular: {
            ...DefaultTheme.fonts.regular,
            fontFamily: theme.fonts.bodyMedium.fontFamily,
          },
          medium: {
            ...DefaultTheme.fonts.medium,
            fontFamily: theme.fonts.titleMedium.fontFamily,
          },
          bold: {
            ...DefaultTheme.fonts.bold,
            fontFamily: theme.fonts.headlineSmall.fontFamily,
          },
          heavy: {
            ...DefaultTheme.fonts.heavy,
            fontFamily: theme.fonts.headlineLarge.fontFamily,
          },
        },
      }}
    >
      {props.children}
    </ThemeProvider>
  );
}

export function App() {
  return (
    <AppPaperProvider>
      <ExpoRouterThemeProvider>
        <InitializeSshKey>
            <Stack />
        </InitializeSshKey>
      </ExpoRouterThemeProvider>
    </AppPaperProvider>
  );
}
