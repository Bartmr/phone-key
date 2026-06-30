import SshModule from "@/modules/main/src/SshModule";
import { ReactNode, useEffect, useState } from "react";

export function InitializeSshKey(props: { children: ReactNode }) {
    const [initialized,setInitialized] = useState(false);

    useEffect(() => {
        SshModule.initializeKey();
        setInitialized(true);
    }, [])

    if (!initialized) {
        return null;
    }

    return <>{props.children}</>
}