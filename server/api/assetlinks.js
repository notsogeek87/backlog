// Fingerprint of app/debug.keystore (builds installed outside the Play Store).
const DEBUG_SHA256 = '8F:2F:E2:D4:F4:74:76:E3:0C:45:4D:21:FD:61:DD:7C:32:E2:2C:94:77:7D:7A:14:EF:F8:22:63:16:F0:F3:FF';

/**
 * GET /.well-known/assetlinks.json — lets Android verify that library.lielu.eu/open/... belongs to the app,
 * so those links open it directly (no chooser, no browser) when it is installed.
 * ASSETLINKS_SHA256 (comma-separated) adds the Play App Signing / release certificate fingerprints.
 */
export default function handler(req, res) {
  const extra = (process.env.ASSETLINKS_SHA256 ?? '').split(',').map((s) => s.trim().toUpperCase()).filter((s) => /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(s));
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', 'public, max-age=0, s-maxage=3600');
  return res.status(200).send(JSON.stringify([{
    relation: ['delegate_permission/common.handle_all_urls'],
    target: { namespace: 'android_app', package_name: 'com.davidgcd.backlog', sha256_cert_fingerprints: [...new Set([DEBUG_SHA256, ...extra])] },
  }]));
}
