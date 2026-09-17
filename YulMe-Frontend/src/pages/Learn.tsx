import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppShell from '../components/AppShell';
import SectionHeader from '../components/SectionHeader';
import { api, ChildResponse, CurriculumSummary, ApiError } from '../lib/api';

export default function Learn() {
  const nav = useNavigate();
  const [children, setChildren] = useState<ChildResponse[]>([]);
  const [curricula, setCurricula] = useState<CurriculumSummary[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([api.listChildren(), api.listCurricula()])
      .then(([kids, catalog]) => { setChildren(kids); setCurricula(catalog); })
      .catch((e: any) => setError(e?.message || 'Could not load the learning catalog.'));
  }, []);

  async function openCurriculum(curriculum: CurriculumSummary) {
    const child = children[0];
    if (!child) { nav('/app'); return; }
    setBusyId(curriculum.id); setError('');
    try {
      const enrollment = await api.enroll(child.id, curriculum.id);
      const item = await api.getNextActivity(enrollment.id);
      nav(`/app/activity/${item.activityVersionId}?item=${item.id}&enrollment=${enrollment.id}`);
    } catch (e: any) {
      if (e instanceof ApiError && e.status === 404) {
        setError(`No published activities are available yet for ${curriculum.name}.`);
      } else {
        setError(e?.message || 'Could not open this learning adventure.');
      }
    } finally { setBusyId(null); }
  }

  return <AppShell>
    <div className="page-title"><div><span className="eyebrow">YOUR LEARNING PATH</span><h1>Choose an adventure.</h1><p>These are real published curricula. Pick one to open its next activity.</p></div></div>
    {error && <div className="form-error" style={{ marginBottom: 20 }}>{error}</div>}
    <SectionHeader title="All adventures" sub="Explore your published learning paths." />
    {curricula.length === 0 ? <div className="insight-card"><h3>No published curriculum yet</h3><p>A teacher or admin can publish one from Teacher Studio.</p></div> : <div className="learn-grid">
      {curricula.map((c, i) => <button key={c.id} onClick={() => openCurriculum(c)} disabled={!!busyId} className={`learn-card ${['blue','purple','yellow','green'][i % 4]}`} style={{ textAlign:'left', width:'100%', border:0, font: 'inherit' }}>
        <div className="learn-card-head"><div className="subject-icon">{c.subjectCode.slice(0,1).toUpperCase()}</div><span>{c.ageHubName}</span></div>
        <h3>{c.name}</h3><p>{c.subjectName} · {c.subjectCode}</p>
        <div className="mini-progress"><span style={{ width:'0%' }} /></div>
        <footer><span>{c.ageHubName}</span><span>{busyId === c.id ? 'Opening…' : 'Open →'}</span></footer>
      </button>)}
    </div>}
    <div className="encourage"><span>🦋</span><div><b>There’s no rush.</b><p>YulMe adapts when evidence shows a child needs more practice.</p></div></div>
  </AppShell>;
}
