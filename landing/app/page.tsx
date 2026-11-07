import { PhonePreview } from "./phone-preview";
import { container, DownloadButton, features, SiteHeader } from "./site";

export default function Home() {
  return (
    <div className="hero relative isolate min-h-svh overflow-hidden">
      <SiteHeader />
      <main id="main-content">
        <section className={`${container} grid max-w-[89rem] items-center gap-5 pt-3 pb-10 sm:gap-8 sm:pt-8 lg:min-h-[min(49rem,calc(100svh_-_7rem))] lg:grid-cols-[1.05fr_0.95fr] lg:gap-4 lg:pt-4 xl:gap-8`}>
          <div className="mx-auto w-full max-w-xs sm:max-w-none lg:col-start-2 lg:row-start-1">
            <PhonePreview />
          </div>

          <div className="min-w-0 text-center lg:col-start-1 lg:row-start-1 lg:text-left">
            <p className="hidden rounded-full bg-tint px-5 py-2 text-lg font-semibold text-brand sm:inline-flex">
              Android Remote App
            </p>
            <h1 className="text-[clamp(2rem,6.1vw,4.6rem)] leading-[1.1] font-bold tracking-[-0.045em] sm:mt-5 lg:text-[clamp(2.75rem,4.15vw,4.6rem)]">
              <span className="sm:hidden">Your phone is the remote.</span>
              <span className="hidden sm:inline">
                One phone.<br />
                Your <span className="text-highlight">computer</span> and <span className="text-highlight">TV.</span>
              </span>
            </h1>
            <p className="mx-auto mt-5 hidden max-w-[35rem] text-xl leading-relaxed text-muted sm:block lg:mx-0 xl:mt-7 xl:text-2xl">
              Use your Android phone as a remote for your computer and compatible TV with simple, practical controls.
            </p>
            <div className="mt-5 sm:mt-7 xl:mt-9"><DownloadButton /></div>
            <ul aria-label="Remote features" className="mt-8 hidden flex-wrap justify-center gap-x-6 gap-y-3 sm:flex lg:justify-start xl:mt-12 xl:gap-x-8">
              {features.map(({ title, icon: Icon }) => (
                <li key={title} className="flex flex-col items-center gap-2 text-center text-sm xl:text-base">
                  <Icon className="size-6 text-highlight" aria-hidden="true" />
                  {title}
                </li>
              ))}
            </ul>
          </div>
        </section>
      </main>
    </div>
  );
}
