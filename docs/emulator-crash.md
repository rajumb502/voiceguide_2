# Emulator crash investigation — 12 November 2026

## Confirmed evidence

Host journal and systemd core-dump metadata show that the emulator exited at
**15:17:24 IST** with SIGSEGV (signal 11). Process 199823 was the Android emulator's
`qemu-system-x86_64`, running AVD `a34play`, emulator version 36.3.10.

The faulting thread's stack includes, in order:

```text
libcuda.so
cuMemcpy2D_v2
media_cuda_utils_nv12_updater
FrameBuffer::Impl::updateYUVTextures
MediaTexturePool::saveDecodedFrameToTexture
MediaCudaVideoHelper::HandlePictureDisplay
libnvcuvid.so
MediaCudaVideoHelper::decode
MediaH264DecoderGeneric::try_decode
MediaH264DecoderGeneric::decodeFrame
```

At 15:17:23, the kernel also logged `vmemmap alloc failure: order:9` on a
`MainLoopThread`, with calls through NVIDIA UVM's `uvm_devmem_init` and `add_gpu`.
This is an allocation failure, not proof that the host OOM killer killed the emulator.
The journal explicitly records a segmentation fault.

The effective `hardware-qemu.ini` used host graphics and 2048 MB guest RAM, even though
the editable AVD config currently contains different settings. Launch logs identify
Intel Mesa OpenGL rendering, while the crash uses NVIDIA CUDA video decoding. Changing
only the OpenGL GPU may therefore leave the failing decoder path in use.

The saved core can be inspected with:

```bash
coredumpctl info 199823 --no-pager
journalctl -k --since '2026-11-12 15:16:30' --until '2026-11-12 15:18:00'
```

This confirms a host emulator failure in its NVIDIA video-decoding path. A page video
is a plausible trigger, but neither the exact video nor the ultimate driver/emulator
bug has been proven. No app Java stack trace was available. The separate ADB SIGABRT
at 15:18:52 came from an attempted sandbox connection during this investigation and
is unrelated to the original emulator crash.

## Focused reproduction

Close any running instance of this AVD, then run in a **normal host terminal**:

```bash
./scripts/emulator-debug.sh a34play
```

The script uses `-feature -HardwareDecoder`, a feature present in the installed
emulator's `lib/advancedFeatures.ini`. It disables the emulated hardware video decoder
for this run and cold-boots without loading/saving snapshots. It does not wipe user
data or modify AVD settings. Standard output, standard error, and the process exit
status are saved under `diagnostics/<timestamp>/`.

In a second host terminal, while the emulator is booting:

```bash
mkdir -p diagnostics
adb -s emulator-5554 wait-for-device
adb -s emulator-5554 logcat -b all -v threadtime > diagnostics/android-logcat.txt
```

Use the serial from `adb devices -l` if it differs. Keep logcat running while opening
the app and navigating to Amazon. Do not restrict capture to the app PID: renderer,
media, and Android system events are relevant. Stop capture with Ctrl+C after reproduction.
If the emulator exits again, also collect `coredumpctl list` and inspect the new QEMU PID.
An ADB disconnect by itself is not evidence of an app crash.

If Amazon now loads, that supports the decoder workaround; it does not prove the app
has no other bugs. If it still crashes, compare the new core stack before changing
additional settings. Opening the same site in the emulator's Chrome and on a physical
Android phone can further distinguish this app's behavior from emulator behavior.

Logs may include browsing/account information; review them before sharing. The core
dump is large and contains process memory, so start with its text backtrace.

The reproduction has not been run by Codex: this session's sandbox prevents ADB from
binding its local port (`Operation not permitted`). No emulator settings or app behavior
were changed during the investigation.

## References

- [Android emulator graphics acceleration](https://developer.android.com/studio/run/emulator-acceleration)
- [Android emulator troubleshooting](https://developer.android.com/studio/run/emulator-troubleshooting)
