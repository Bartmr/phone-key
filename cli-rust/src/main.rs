use bluer::gatt::remote::{Characteristic, CharacteristicWriteRequest};
use bluer::gatt::WriteOp;
use std::str::FromStr;
use uuid::Uuid;

const CHARACTERISTIC_UUID: &str = "69924d24-8e47-4d43-9e86-dde30201a474";
const CHUNK: &[u8] = b"The quick brown fox jumps over the lazy dog. ";
const PAYLOAD_SIZE: usize = 10_240;
const DEVICE_ADDERSS: &'static str = "74:E1:47:6C:B4:18";

#[tokio::main]
async fn main() -> bluer::Result<()> {
    let device_address = DEVICE_ADDERSS;
    let char_uuid = Uuid::from_str(CHARACTERISTIC_UUID)
        .expect("invalid characteristic UUID");

    println!("Connecting to {device_address}...");

    let session = bluer::Session::new().await?;
    let adapter = session.default_adapter().await?;
    adapter.set_powered(true).await?;

    let addr = device_address.parse().expect("invalid Bluetooth address format");
    let device = adapter.device(addr)?;

    device.connect().await?;
    
    let char = find_characteristic(&device, char_uuid).await?;

    let payload: Vec<u8> = CHUNK
        .iter()
        .copied()
        .cycle()
        .take(PAYLOAD_SIZE)
        .collect();
    println!("Sending {} bytes via reliable long write...", payload.len());
    char.write_ext(&payload, &CharacteristicWriteRequest {
        offset: 0,
        op_type: WriteOp::Reliable,
        ..Default::default()
    }).await?;
    println!("Done. BlueZ handled prepare-write + execute-write internally.");

    device.disconnect().await?;

    Ok(())
}

async fn find_characteristic(device: &bluer::Device, target_uuid: Uuid) -> bluer::Result<Characteristic> {
    let services = device.services().await?;
    println!("Found {} services:", services.len());
    for service in &services {
        println!("  {}", service.id());
    }
    for service in services {
        for char in service.characteristics().await? {
            let uuid = char.uuid().await?;
            if uuid == target_uuid {
                return Ok(char);
            }
        }
    }
    panic!("characteristic {target_uuid} not found");
}
