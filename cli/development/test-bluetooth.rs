use phone_key_cli::{bluetooth, config};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let config = config::Config::load()?;
    let device_address = config
        .device_address
        .as_deref()
        .expect("device_address not set in ~/.phone-key.json");

    let mut conn = bluetooth::BluetoothConnection::connect(device_address).await?;

    let large_payload = "X".repeat(2000);
    let message = serde_json::json!({
        "type": "echo",
        "payload": large_payload
    })
    .to_string();

    println!("Sending {} bytes over BLE...", message.len());

    let response = conn.send_message(&message).await?;

    println!(
        "Received {} bytes: {}",
        response.len(),
        String::from_utf8_lossy(&response)
    );

    conn.disconnect().await?;

    Ok(())
}
