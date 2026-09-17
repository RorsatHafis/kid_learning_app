import { useEffect, useState } from 'react';
import AppShell from '../components/AppShell';
import { api, ClassResponse, SchoolResponse, TeacherClass } from '../lib/api';

export default function PrincipalDashboard() {
  const [schools, setSchools] = useState<SchoolResponse[]>([]);
  const [classes, setClasses] = useState<TeacherClass[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([api.listSchools(), api.listMyClasses()])
      .then(([schoolRows, classRows]) => { setSchools(schoolRows); setClasses(classRows); })
      .catch((e: any) => setError(e?.message || 'Could not load school data.'))
      .finally(() => setLoading(false));
  }, []);

  const school = schools[0];
  return <AppShell workspace="principal">
    <div className="page-title workspace-title">
      <div><span className="eyebrow">SCHOOL LEADERSHIP</span><h1>Lead learning with clarity.</h1><p>Monitor your school structure and learning community from one professional workspace.</p></div>
    </div>
    {loading && <p>Loading school data…</p>}
    {error && <div className="form-error">{error}</div>}
    {!loading && !error && <div className="leadership-grid">
      <section className="leadership-hero"><span className="workspace-badge">PRINCIPAL</span><h2>{school?.name || 'Your school'}</h2><p>{school ? 'Your school workspace is connected to the platform.' : 'No school has been assigned to this account yet.'}</p></section>
      <section className="leadership-stat"><strong>{classes.length}</strong><span>Classes</span><small>Classes in your school workspace</small></section>
      <section className="leadership-stat"><strong>{school ? 'Active' : 'Pending'}</strong><span>School access</span><small>Principal workspace status</small></section>
      <section className="leadership-panel"><div className="panel-head"><div><h2>Classes</h2><p>School classes available to your account.</p></div></div>{classes.length === 0 ? <p>No classes are available yet.</p> : <div className="class-list">{classes.map(c => <div key={c.id}><div className="class-avatar">{c.name.slice(0,1).toUpperCase()}</div><div><b>{c.name}</b><small>School class</small></div></div>)}</div>}</section>
    </div>}
  </AppShell>;
}
