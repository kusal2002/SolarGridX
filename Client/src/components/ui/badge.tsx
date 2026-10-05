import type { ComponentProps } from "react"
export function Badge({ className = "", ...props }: ComponentProps<"span">) {
  return <span data-slot="badge" className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-medium ${className}`} {...props} />
}
