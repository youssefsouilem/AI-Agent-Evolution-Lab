import { useCallback, useEffect, useMemo, useState } from 'react'
import { alertsApi } from '../../services/monitoringApi'
import { ALERT_STATUSES, SEVERITIES } from '../../components/anomaly-monitoring/constants'
import { AlertRow, ConfirmDialog, EmptyState, ErrorBanner, Field, Loading, Modal, PageHeader, SearchInput, SelectField, SectionHeader, Stats, ListCard, TextareaField } from '../../components/anomaly-monitoring/ui'
import '../../components/anomaly-monitoring/anomaly-monitoring.css'

const blank = { severity: 'MEDIUM', message: '', anomalyCheckId: '' }
export default function AlertsPage() {
  const [items, setItems] = useState([]), [loading, setLoading] = useState(true), [error, setError] = useState(null)
  const [query, setQuery] = useState(''), [status, setStatus] = useState('ALL'), [severity, setSeverity] = useState('ALL')
  const [modal, setModal] = useState(null), [remove, setRemove] = useState(null), [toast, setToast] = useState('')
  const load = useCallback(async () => { setLoading(true); setError(null); try { setItems(status === 'ALL' ? await alertsApi.list() : await alertsApi.byStatus(status)) } catch (e) { setError(e) } finally { setLoading(false) } }, [status])
  useEffect(() => { load() }, [load])
  const filtered = useMemo(() => items.filter((item) => (!query || String(item.message).toLowerCase().includes(query.toLowerCase())) && (severity === 'ALL' || item.severity === severity)), [items, query, severity])
  const counts = { total: items.length, open: items.filter((x) => ['OPEN', 'NEW'].includes(String(x.status).toUpperCase())).length, ack: items.filter((x) => String(x.status).toUpperCase() === 'ACKNOWLEDGED').length, resolved: items.filter((x) => String(x.status).toUpperCase() === 'RESOLVED').length }
  const save = async (data) => { try { const result = modal.item ? await alertsApi.update(modal.item.id, data) : await alertsApi.create(data); setItems((old) => modal.item ? old.map((x) => x.id === result.id ? result : x) : [result, ...old]); setModal(null); setToast('Alerte enregistrée') } catch (e) { setToast(e.message) } }
  const action = async (id, fn, message) => { try { const result = await fn(id); setItems((old) => old.map((x) => x.id === result.id ? result : x)); setToast(message) } catch (e) { setToast(e.message) } }
  const removeItem = async () => { try { await alertsApi.remove(remove.id); setItems((old) => old.filter((x) => x.id !== remove.id)); setRemove(null); setToast('Alerte supprimée') } catch (e) { setToast(e.message); setRemove(null) } }
  return <div><PageHeader crumb="Alertes" title="Alertes" subtitle="Sélectionnez une alerte pour l'acquitter ou la résoudre." action="Créer une alerte" onAction={() => setModal({ item: null })} />
    <Stats items={[{ label: 'Total des alertes', value: counts.total }, { label: 'Ouvertes', value: counts.open }, { label: 'Acquittées', value: counts.ack }, { label: 'Résolues', value: counts.resolved }]} />
    <div className="m6-filters"><SearchInput value={query} onChange={setQuery} placeholder="Rechercher dans les messages…" /><label className="m6-select"><select value={status} onChange={(e) => setStatus(e.target.value)}><option value="ALL">Toutes les alertes</option>{ALERT_STATUSES.map((x) => <option key={x} value={x}>{x}</option>)}</select></label><label className="m6-select"><select value={severity} onChange={(e) => setSeverity(e.target.value)}><option value="ALL">Toutes les sévérités</option>{SEVERITIES.map((x) => <option key={x}>{x}</option>)}</select></label></div>
    {error && <ErrorBanner error={error} retry={load} />}{loading ? <Loading /> : <><SectionHeader title="Alertes à traiter" count={`${filtered.length} alerte(s)`} /><ListCard>{filtered.length ? filtered.map((item) => <AlertRow key={item.id} item={item} onAcknowledge={() => action(item.id, alertsApi.acknowledge, 'Alerte acquittée')} onResolve={() => action(item.id, alertsApi.resolve, 'Alerte résolue')} onEdit={() => setModal({ item })} onDelete={() => setRemove(item)} />) : <EmptyState />}</ListCard></>}
    {modal && <Modal title={modal.item ? 'Modifier l’alerte' : 'Créer une alerte'} onClose={() => setModal(null)} onSubmit={() => save(modal.form || { ...blank })}><AlertForm item={modal.item} onChange={(form) => setModal((old) => ({ ...old, form }))} /></Modal>}
    {remove && <ConfirmDialog title="Supprimer cette alerte ?" onCancel={() => setRemove(null)} onConfirm={removeItem} />}{toast && <div className="m6-toast" onAnimationEnd={() => setToast('')}>{toast}</div>}
  </div>
}
function AlertForm({ item, onChange }) { const [form, setForm] = useState({ ...blank, ...item, anomalyCheckId: item?.anomalyCheckId || '' }); const update = (next) => { setForm(next); onChange(next) }; return <div className="m6-form-grid"><SelectField label="Sévérité" value={form.severity} onChange={(e) => update({ ...form, severity: e.target.value })}>{SEVERITIES.map((x) => <option key={x}>{x}</option>)}</SelectField><Field label="ID de vérification" type="number" value={form.anomalyCheckId} onChange={(e) => update({ ...form, anomalyCheckId: e.target.value ? Number(e.target.value) : null })} /><TextareaField label="Message" className="m6-full" required value={form.message} onChange={(e) => update({ ...form, message: e.target.value })} /></div> }
