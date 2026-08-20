# Dextop Google Cast Web Receiver

1. Host this directory on a public HTTPS origin. This repository publishes it
   at `https://naryuki.github.io/Dextop/google-cast-receiver/`.
2. Register that URL as a Custom Web Receiver in the Google Cast SDK Console.
3. Put the resulting application ID in `DEXTOP_CAST_RECEIVER_APP_ID` in `.env`.
4. Build Dextop again.

The receiver uses the custom namespace `urn:x-cast:moe.n4tsu.dextop` and the
Cast Application Framework media channel. Miracast is intentionally not used.
