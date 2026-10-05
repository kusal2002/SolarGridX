import { Input } from "@/components/ui/input"
export type StationOperator = { nic: string; name: string }
export function OperatorSelector({ operators, value, onChange, disabled = false }: { operators: StationOperator[]; value: string[]; onChange: (nics: string[]) => void; disabled?: boolean }) {
  const options = [...operators, ...value.filter(nic => !operators.some(o => o.nic === nic)).map(nic => ({ nic, name: "Inactive operator" }))]
  return <div className="space-y-2">
    <p className="text-sm text-muted-foreground">Select one or more operators. Leave empty to assign later.</p>
    <div className="max-h-40 space-y-1 overflow-y-auto rounded-lg border p-2">
      {!options.length && <p className="p-2 text-sm text-muted-foreground">No active Grid Operators. Create an operator account first.</p>}
      {options.map(operator => <label key={operator.nic} className="flex cursor-pointer items-center gap-3 rounded-md p-2 hover:bg-muted">
        <Input type="checkbox" className="size-4 shrink-0" checked={value.includes(operator.nic)} disabled={disabled || (!operators.some(o => o.nic === operator.nic) && !value.includes(operator.nic))} onChange={e => onChange(e.target.checked ? [...value, operator.nic] : value.filter(n => n !== operator.nic))} />
        <span className="text-sm">{operator.name}<span className="ml-2 text-xs text-muted-foreground">{operator.nic}</span></span>
      </label>)}
    </div>
  </div>
}
