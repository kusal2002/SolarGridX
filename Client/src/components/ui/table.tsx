import type { ComponentProps } from "react"
export function Table({className = "", ...props}: ComponentProps<"table">) { return <div className="relative w-full overflow-x-auto"><table data-slot="table" className={`w-full caption-bottom text-sm ${className}`} {...props} /></div> }
export function TableHeader(props: ComponentProps<"thead">) { return <thead data-slot="table-header" {...props} /> }
export function TableBody(props: ComponentProps<"tbody">) { return <tbody data-slot="table-body" {...props} /> }
export function TableRow({className = "", ...props}: ComponentProps<"tr">) { return <tr data-slot="table-row" className={`border-b transition-colors hover:bg-muted/40 last:border-0 ${className}`} {...props} /> }
export function TableHead({className = "", ...props}: ComponentProps<"th">) { return <th data-slot="table-head" className={`h-11 whitespace-nowrap px-4 text-left font-medium text-muted-foreground ${className}`} {...props} /> }
export function TableCell({className = "", ...props}: ComponentProps<"td">) { return <td data-slot="table-cell" className={`p-4 align-middle ${className}`} {...props} /> }
