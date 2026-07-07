use phone_key_cli::{bluetooth, config};
use std::sync::OnceLock;
use tokio::sync::Mutex;

/// Static handle so the panic hook can access the connection for cleanup.
static CONN: OnceLock<Mutex<Option<bluetooth::BluetoothConnection>>> = OnceLock::new();

/// Attempt to disconnect on panic — best-effort cleanup.
fn disconnect_on_panic() {
    if let Some(mutex) = CONN.get() {
        eprintln!("\nDisconnecting due to panic...");
        // Panic hooks run synchronously, so we spin up a temporary runtime
        // to drive the async disconnect future.
        if let Ok(rt) = tokio::runtime::Runtime::new() {
            rt.block_on(async {
                if let Some(ref conn) = *mutex.lock().await {
                    let _ = conn.disconnect().await;
                }

            });
        }
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // ---- panic hook ----
    let prev_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        disconnect_on_panic();
        prev_hook(info);
    }));

    // ---- connect ----
    let config = config::Config::load()?;
    let device_address = config
        .device_address
        .as_deref()
        .expect("device_address not set in ~/.phone-key.json");

    let conn = bluetooth::BluetoothConnection::connect(device_address).await?;
    let conn_mutex: &Mutex<Option<bluetooth::BluetoothConnection>> =
        CONN.get_or_init(|| Mutex::new(Some(conn)));

    // ---- prepare message ----
    let large_payload = "X".repeat(2000);
    let message = serde_json::json!({
        "type": "echo",
        "data": large_payload
    })
    .to_string();
    println!("Sending {} bytes over BLE...", message.len());

    // Take ownership of the connection for the duration of the exchange.
    let mut conn = conn_mutex.lock().await.take().unwrap();

    // ---- race: normal operation vs Ctrl+C ----
    tokio::select! {
        result = conn.send_message(&message) => {
            match result {
                Ok(response) => {
                    println!(
                        "Received {} bytes: {}",
                        response.len(),
                        String::from_utf8_lossy(&response)
                    );
                }
                Err(e) => {
                    eprintln!("Error sending message: {e}");
                    conn.disconnect().await?;
                    return Err(e.into());
                }
            }
        }
        _ = tokio::signal::ctrl_c() => {
            eprintln!("\nCtrl+C received, disconnecting...");
            conn.disconnect().await?;
            println!("Disconnected.");
            return Ok(());
        }
    }

    // ---- clean disconnect ----
    conn.disconnect().await?;
    println!("Disconnected.");

    Ok(())
}
