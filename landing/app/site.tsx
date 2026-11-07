import Image from "next/image";
import Link from "next/link";
import { Download, Keyboard, Monitor, MousePointer2, Play, Presentation, Smartphone } from "lucide-react";

export const container = "mx-auto w-[calc(100%_-_2.5rem)] sm:w-[86%]";

export const features = [
  { title: "Touchpad", icon: MousePointer2 },
  { title: "Keyboard", icon: Keyboard },
  { title: "Media", icon: Play },
  { title: "Presentation", icon: Presentation },
  { title: "TV Remote", icon: Monitor },
];

export function DownloadButton({ compact = false }: { compact?: boolean }) {
  const downloadUrl =
    process.env.APK_DOWNLOAD_URL ||
    "https://github.com/kuldeeprajput-dev/AIO-remote/releases/download/v1.0.0/AioRemote.apk";
  const Icon = compact ? Download : Smartphone;
  const size = compact ? "rounded-full px-4 text-sm sm:px-6 sm:text-base" : "rounded-2xl px-6 py-3 sm:px-8 sm:py-4 sm:text-xl";

  return (
    <a className={`inline-flex min-h-11 items-center justify-center gap-2.5 bg-brand font-bold text-white transition-colors hover:bg-blue-800 ${size}`} href={downloadUrl}>
      <Icon className="size-5 shrink-0" aria-hidden="true" />
      {compact ? "Download" : "Download for Android"}
    </a>
  );
}

export function SiteHeader() {
  return (
    <>
      <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-20 focus:rounded-lg focus:bg-white focus:p-3">
        Skip to content
      </a>
      <header className={`${container} flex max-w-[89rem] flex-wrap items-center justify-between gap-x-4 gap-y-2 py-4 sm:gap-6 lg:py-8`}>
        <Link href="/" className="inline-flex min-h-11 items-center gap-2.5 text-lg font-bold tracking-tight sm:text-3xl">
          <Image src="/icon.svg" width={32} height={32} alt="" unoptimized className="size-9 rounded-lg sm:size-12" />
          AIO Remote
        </Link>
        <div className="hidden sm:block"><DownloadButton compact /></div>
      </header>
    </>
  );
}
