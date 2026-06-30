import { useSpacing } from "@/ui/use-spacing";
import { ReactNode, useEffect, useRef, useState } from "react";
import { AppState, PermissionsAndroid, View } from "react-native";
import { Button, Text, useTheme } from "react-native-paper";

import BluetoothModule from "@/modules/main/src/BluetoothModule";


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

function ServerGate(props: { children: ReactNode }) {
    const [isStarted, setIsStarted] = useState(false);
    const appStateRef = useRef(AppState.currentState);

    useEffect(() => {
        const sub = AppState.addEventListener("change", (nextAppState) => {
            void (async () => {
                if (
                    (
                        appStateRef.current === "inactive"
                        || appStateRef.current === "background"
                    ) && nextAppState === "active"
                ) {
                    BluetoothModule.startGattServer();
                    setIsStarted(true);
                } else if (
                    appStateRef.current === "active"
                    && (nextAppState === "inactive" || nextAppState === "background")
                ) {
                    BluetoothModule.stopGattServer();
                    setIsStarted(false);
                }
            })();

            appStateRef.current = nextAppState;
        });

        return () => sub.remove();
    }, []);

    useEffect(() => {
        void (async () => {
            BluetoothModule.startGattServer();
            setIsStarted(true);
        })();

        return () => {
            BluetoothModule.stopGattServer();
        };
    }, []);

    if (!isStarted) {
        return null;
    }

    return <>{props.children}</>;
}

export function BluetoothGate(props: { children: ReactNode }) {
    return (
        <PermissionsGate>
            <ServerGate>{props.children}</ServerGate>
        </PermissionsGate>
    );
}