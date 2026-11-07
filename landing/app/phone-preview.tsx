import {
  ChevronDown, ChevronLeft, ChevronRight, ChevronUp, Hand, House, Menu,
  Minus, Monitor, Mouse, Plus, Power, Settings, Smartphone, Volume2, VolumeX,
} from "lucide-react";

export function PhonePreview() {
  return (
    <figure className="phone-preview relative mx-auto w-full max-w-[31rem] lg:max-w-[40rem]" role="img" aria-label="Two AIO Remote previews showing the touchpad and TV remote">
      <div aria-hidden="true">
        <div className="phone phone--tv">
          <div className="phone-tabs"><span>Presentation</span><b>TV Remote</b><span>Shortcuts</span></div>
          <div className="phone-actions"><Power className="text-red-600" /><House /><Menu /></div>
          <div className="phone-dpad">
            <ChevronUp /><ChevronLeft /><span>OK</span><ChevronRight /><ChevronDown />
          </div>
          <div className="phone-volume"><VolumeX /><Minus /><Volume2 /><Plus /></div>
        </div>
        <div className="phone phone--touch">
          <div className="phone-brand"><span><Smartphone /></span>AIO Remote</div>
          <div className="phone-tabs"><b>Touchpad</b><span>Keyboard</span><span>Media</span></div>
          <div className="phone-pad"><Hand /><span>Move your finger to control</span></div>
          <div className="phone-clicks"><span><Mouse />Left Click</span><span><Mouse />Right Click</span></div>
          <div className="phone-nav">
            <span><Monitor />Devices</span><span><i><Smartphone /></i>Remote</span><span><Settings />Settings</span>
          </div>
        </div>
      </div>
    </figure>
  );
}
