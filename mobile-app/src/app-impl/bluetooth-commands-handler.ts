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

const IncomingCommandMessageSchema = z.union([
                z.object({
                    command: z.literal("sign"),
                    data: z.string() // Base64
                }),
                z.object({
                    command: z.literal("get-public-key")
                })
            ])


export function useBluetoothCommandsHandler() {
    const stateRef = useRef<State | null>(null)
    const [uiState, setUiState] = useState<UIState | null>(null);


    useEffect(() => {
        const subscription = BluetoothModule.addListener("onDataReceived", async (event) => {
            const decoder = new TextDecoder('utf-8');
            const eventDataString = decoder.decode(event.data);
            const message = JSON.parse(eventDataString);

            const validatedMessage = IncomingCommandMessageSchema.parse(message);

            if (validatedMessage.command === "sign") {
                const dataBytes = Uint8Array.from(atob(validatedMessage.data), c => c.charCodeAt(0));
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

                BluetoothModule.enqueueDataToRead(signed.signature);
            } else if (validatedMessage.command === "get-public-key") {
                const publicKey = await SshModule.getPublicKey();
                const encoder = new TextEncoder();
                BluetoothModule.enqueueDataToRead(encoder.encode(publicKey));
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