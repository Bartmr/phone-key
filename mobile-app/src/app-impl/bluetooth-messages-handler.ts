import BluetoothModule from "@/modules/main/src/BluetoothModule";
import SshModule from "@/modules/main/src/SshModule";
import { SshSignError } from "@/modules/main/src/SshModule.types";
import { useEffect, useMemo, useRef, useState } from "react";
import { z } from 'zod'



type State = never

type UIState = {
    command: "sign",
    error?: SshSignError
}

const MessageSchema = z.union([
                z.object({
                    type: z.literal("sign"),
                    data: z.string() // Base64
                }),
                z.object({
                    type: z.literal("get-public-key")
                })
            ])


export function useBluetoothMessagesHandler() {
    const stateRef = useRef<State | null>(null)
    const [uiState, setUiState] = useState<UIState | null>(null);


    useEffect(() => {
        const subscription = BluetoothModule.addListener("onDataReceived", async (event) => {
            const decoder = new TextDecoder('utf-8');
            const eventDataString = decoder.decode(event.data);
            const rawMessage = JSON.parse(eventDataString);
            const message = MessageSchema.parse(rawMessage);

            if (message.type === "sign") {
                const dataBytes = Uint8Array.from(atob(message.data), c => c.charCodeAt(0));
                const signed = await SshModule.sign(dataBytes);

                if (signed.error) {
                    setUiState({
                        command: "sign",
                        error: signed.error
                    })
                }

                if (!signed.signature) {
                    throw new Error();
                }

                BluetoothModule.sendToClient(signed.signature);
            } else if (message.type === "get-public-key") {
                const publicKey = await SshModule.getPublicKey();
                const encoder = new TextEncoder();
                BluetoothModule.sendToClient(encoder.encode(publicKey));
            }
        })

        return () => {
            subscription.remove();
        }
    }, [])

    return useMemo(() => {
        return {
            uiState
        }
    }, [uiState])
}