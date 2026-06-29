import { useSpacing } from "@/ui/use-spacing";
import { ReactNode, useEffect, useState } from "react";
import { PermissionsAndroid, View } from "react-native";
import { Button, Text, useTheme } from "react-native-paper";


function PermissionsGate(props: { children: ReactNode }) {
    const theme = useTheme();
    const spacing = useSpacing();
    const [permissionsRequested, setPermissionsRequested] = useState(false);
    const [permissionsGranted, setPermissionsGranted] = useState(false);

    const requestPermissions = () => {
        setPermissionsRequested(true);

        /*
            Also referenced in modules/main/android/src/main/AndroidManifest.xml
        */
        const BLUETOOTH_PERMISSIONS = [
            PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
            PermissionsAndroid.PERMISSIONS.BLUETOOTH_ADVERTISE,
            PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
        ]

        PermissionsAndroid.requestMultiple(BLUETOOTH_PERMISSIONS).then((result) => {
            setPermissionsGranted(
            BLUETOOTH_PERMISSIONS.every((perm) => result[perm] === PermissionsAndroid.RESULTS.GRANTED),
            );
        });
    };

    useEffect(() => {
        requestPermissions();
    }, []);

    if (permissionsRequested) {
        return <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', gap: spacing.dp8 }}>
            <Text variant="bodyLarge" style={{ color: theme.colors.onBackground, textAlign: 'center' }}>
              Bluetooth permissions are required to encrypt and decrypt data.
            </Text>
            <Button mode="contained" onPress={requestPermissions}>
              Grant Permissions
            </Button>
          </View>
    } else if (permissionsGranted) {
        return <>{props.children}</>
    } else {
        return null;
    }
}

export function BluetoothGate(props: { children: ReactNode }) {
    return <PermissionsGate>{props.children}</PermissionsGate>
}