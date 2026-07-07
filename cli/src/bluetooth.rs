use bluer::gatt::remote::{Characteristic, CharacteristicWriteRequest};
use bluer::gatt::WriteOp;
use bluer::{Device};
use futures::{Stream, StreamExt};
use std::pin::Pin;
use std::str::FromStr;
use std::sync::Arc;
use tokio::sync::Mutex;
use uuid::Uuid;

const SERVICE_UUID: &str = "a667f940-6a50-49ac-9b75-2b9639564972";
const CHARACTERISTIC_UUID: &str = "69924d24-8e47-4d43-9e86-dde30201a474";

pub struct BluetoothConnection {
    device: Device,
    characteristic: Characteristic,
    notifications: Arc<Mutex<Pin<Box<dyn Stream<Item = Vec<u8>> + Send>>>>,
}

#[derive(Debug)]
pub enum Error {
    Bluetooth(bluer::Error),
    Timeout,
    StreamEnded,
    EmptyResponse,
    Utf8(std::string::FromUtf8Error),
    NotFound(String),
    Unknown(String)
}

impl std::fmt::Display for Error {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Error::Bluetooth(e) => write!(f, "Bluetooth error: {e}"),
            Error::Timeout => write!(f, "timed out waiting for response signal"),
            Error::StreamEnded => write!(f, "notification stream ended unexpectedly"),
            Error::EmptyResponse => write!(f, "empty response received"),
            Error::Utf8(e) => write!(f, "UTF-8 error: {e}"),
            Error::NotFound(msg) => write!(f, "{msg}"),
            Error::Unknown(msg) => write!(f, "{msg}"),
        }
    }
}

impl std::error::Error for Error {}

impl From<bluer::Error> for Error {
    fn from(e: bluer::Error) -> Self {
        Error::Bluetooth(e)
    }
}

impl BluetoothConnection {
    pub async fn connect(device_address: &str) -> Result<Self, Error> {
        let session = bluer::Session::new().await?;
        let adapter = session.default_adapter().await?;
        adapter.set_powered(true).await?;

        let addr = device_address
            .parse()
            .expect("invalid Bluetooth address format");
        let device = adapter.device(addr)?;
        

        // Force service LTE discovery
        {
            let dbus_path_mac = device_address.replace(':', "_");
            let status = std::process::Command::new("dbus-send")
                .args([
                    "--system",
                    "--print-reply",
                    "--dest=org.bluez",
                    &format!("/org/bluez/hci0/dev_{dbus_path_mac}"),
                    "org.bluez.Device1.Connect",
                ])
                .status()
                .map_err(|e| {
                    let msg = format!("failed to run dbus-send for Device1.Connect: {e}");
                    eprintln!("[bluetooth] {msg}");
                    Error::Unknown(msg)
                })?;
            if !status.success() {
                let msg = format!(
                    "dbus-send Device1.Connect exited with {status}"
                );
                eprintln!("[bluetooth] {msg}");
                return Err(Error::Unknown(msg));
            }
        }

        eprintln!("[bluetooth] device connected, waiting for services to resolve...");


        // BLE service discovery runs asynchronously after connect(). We must
        // wait until BlueZ has finished enumerating GATT services, otherwise
        // device.services() returns an empty list for a freshly connected
        // Android GATT-server device.
        {
            let deadline = tokio::time::Instant::now() + std::time::Duration::from_secs(10);
            loop {
                if device.is_services_resolved().await? {
                    break;
                }
                if tokio::time::Instant::now() >= deadline {
                    return Err(Error::NotFound(
                        "services not resolved within 10 s after connect".into(),
                    ));
                }
                tokio::time::sleep(std::time::Duration::from_millis(100)).await;
            }
        }

        eprintln!("[bluetooth] services resolved...");

        let service_uuid = Uuid::from_str(SERVICE_UUID).expect("invalid service UUID");
        let char_uuid = Uuid::from_str(CHARACTERISTIC_UUID).expect("invalid characteristic UUID");

        eprintln!("[bluetooth] discovering services...");
        let services = device.services().await?;
        eprintln!("[bluetooth] found {} service(s)", services.len());

        let characteristic = {
            let mut found = None;
            for service in &services {
                let svc_uuid = service.uuid().await?;
                eprintln!("[bluetooth]   service: {svc_uuid}");
                if svc_uuid != service_uuid {
                    continue;
                }
                for char in service.characteristics().await? {
                    let ch_uuid = char.uuid().await?;
                    eprintln!("[bluetooth]     characteristic: {ch_uuid}");
                    if ch_uuid == char_uuid {
                        found = Some(char);
                        break;
                    }
                }
                if found.is_some() {
                    break;
                }
            }
            found.ok_or_else(|| {
                let msg = format!(
                    "characteristic {CHARACTERISTIC_UUID} not found on device. \
                     Found {count} service(s). \
                     Make sure the Android app is in the foreground with the GATT server running.",
                    count = services.len()
                );
                eprintln!("[bluetooth] {msg}");
                Error::NotFound(msg)
            })?
        };

        eprintln!("[bluetooth] characteristic found, enabling notifications...");

        let notifications = Box::pin(characteristic.notify().await?)
            as Pin<Box<dyn Stream<Item = Vec<u8>> + Send>>;
        let notifications = Arc::new(Mutex::new(notifications));


        eprintln!("[bluetooth] notifications enabled, connection established");

        Ok(Self {
            device,
            characteristic,
            notifications,
        })
    }

    pub async fn send_message(&mut self, json: &str) -> Result<Vec<u8>, Error> {
        self.characteristic
            .write_ext(
                json.as_bytes(),
                &CharacteristicWriteRequest {
                    offset: 0,
                    op_type: WriteOp::Reliable,
                    ..Default::default()
                },
            )
            .await?;

        let notifications = self.notifications.clone();
        let response = tokio::time::timeout(
            std::time::Duration::from_millis(60000),
            async {
                let mut buffer = Vec::new();
                loop {
                    let chunk = notifications.lock().await.next().await
                        .ok_or(Error::StreamEnded)?;
                    if chunk == [0x02] {
                        break;
                    }
                    buffer.extend_from_slice(&chunk);
                }

                if buffer.is_empty() {
                    return Err(Error::EmptyResponse);
                }

                Ok(buffer)
            },
        )
        .await
        .map_err(|_| Error::Timeout)??;

        Ok(response)
    }

    pub async fn disconnect(&self) -> Result<(), Error> {
        drop(self.notifications.lock().await);
        self.device.disconnect().await?;
        Ok(())
    }
}
