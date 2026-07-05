use bluer::gatt::remote::{Characteristic, CharacteristicWriteRequest};
use bluer::gatt::WriteOp;
use bluer::Device;
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

        // Pair if not already bonded.
        let was_paired = device.is_paired().await?;
        if !was_paired {
            eprintln!("[bluetooth] device not paired — initiating pairing (check the Android device for a confirmation dialog)...");
            device.pair().await?;
            eprintln!("[bluetooth] pairing complete");
        } else {
            eprintln!("[bluetooth] device already paired");
        }

        // Trust is required on some BlueZ / Android combinations for encryption to succeed.
        if !device.is_trusted().await? {
            device.set_trusted(true).await?;
            eprintln!("[bluetooth] device set as trusted");
        }

        // Show advertised service UUIDs before connecting (non-invasive check).
        if let Some(uuids) = device.uuids().await? {
            eprintln!("[bluetooth] advertised service UUIDs: {uuids:?}");
            if !uuids.contains(&Uuid::from_str(SERVICE_UUID).unwrap()) {
                eprintln!(
                    "[bluetooth] WARNING: our service UUID is not in the advertisement — \
                     is the Android app running and the GATT server started?"
                );
            }
        } else {
            eprintln!("[bluetooth] no advertised service UUIDs available (device may not have been scanned recently)");
        }

        eprintln!("[bluetooth] connecting...");
        device.connect().await?;
        eprintln!(
            "[bluetooth] connected: services_resolved={}",
            device.is_services_resolved().await?
        );

        let service_uuid = Uuid::from_str(SERVICE_UUID).expect("invalid service UUID");
        let char_uuid = Uuid::from_str(CHARACTERISTIC_UUID).expect("invalid characteristic UUID");

        let services = device.services().await?;
        eprintln!("[bluetooth] found {} GATT service(s)", services.len());

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
                Error::NotFound(format!(
                    "characteristic {CHARACTERISTIC_UUID} not found on device. \
                     Found {count} service(s). \
                     Make sure the Android app is in the foreground with the GATT server running.",
                    count = services.len()
                ))
            })?
        };

        eprintln!("[bluetooth] characteristic found, enabling notifications...");

        let notifications = Box::pin(characteristic.notify().await?)
            as Pin<Box<dyn Stream<Item = Vec<u8>> + Send>>;
        let notifications = Arc::new(Mutex::new(notifications));

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
                    if chunk.is_empty() {
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
