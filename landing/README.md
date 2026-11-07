# AIO Remote landing page

From the repository root:

```sh
cd landing
npm ci
npm run dev
```

Open <http://localhost:3000>. For a production build, run `npm run build`.

The download buttons default to the GitHub release asset for version 1.0.0. The current GitHub repository is private, so only visitors with access can download that asset. To share the APK publicly, set `APK_DOWNLOAD_URL` in `.env.local` or in the hosting environment to a publicly accessible APK URL before building.
