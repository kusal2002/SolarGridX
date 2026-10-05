import { useEffect, useRef, useState } from "react"
import jsQR from "jsqr"

export function QrScanner({ onScan, onClose }: { onScan: (payload: string) => void; onClose: () => void }) {
  const video = useRef<HTMLVideoElement>(null)
  const scanHandler = useRef(onScan)
  const [error, setError] = useState("")
  useEffect(() => { scanHandler.current = onScan }, [onScan])

  useEffect(() => {
    let stopped = false
    let stream: MediaStream | undefined
    let timer: number | undefined
    const stopCamera = () => stream?.getTracks().forEach(track => track.stop())
    async function start() {
      try {
        if (!navigator.mediaDevices?.getUserMedia) throw new Error("Camera requires localhost or HTTPS.")
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" }, audio: false })
        if (stopped || !video.current) { stopCamera(); return }
        video.current.srcObject = stream
        await video.current.play()
        const canvas = document.createElement("canvas")
        const context = canvas.getContext("2d", { willReadFrequently: true })!
        timer = window.setInterval(() => {
          const frame = video.current
          if (stopped || !frame?.videoWidth) return
          canvas.width = Math.min(frame.videoWidth, 800)
          canvas.height = Math.round(canvas.width * frame.videoHeight / frame.videoWidth)
          context.drawImage(frame, 0, 0, canvas.width, canvas.height)
          const pixels = context.getImageData(0, 0, canvas.width, canvas.height)
          const code = jsQR(pixels.data, canvas.width, canvas.height)
          if (code?.data) {
            stopped = true
            window.clearInterval(timer)
            stopCamera()
            scanHandler.current(code.data)
          }
        }, 200)
      } catch {
        stopCamera()
        if (!stopped) setError("Camera unavailable. Allow camera access and use localhost or HTTPS, or scan in the Android app.")
      }
    }
    void start()
    return () => { stopped = true; window.clearInterval(timer); stopCamera() }
  }, [])

  return <section className="space-y-3 rounded-lg border p-4" aria-label="QR camera scanner">
    <p>Point the camera at the prosumer’s booking QR.</p>
    <video ref={video} muted playsInline className="max-h-80 w-full rounded-md" />
    {error && <p role="alert">{error}</p>}
    <button onClick={onClose} className="rounded-md border px-3 py-2">Close camera</button>
  </section>
}
