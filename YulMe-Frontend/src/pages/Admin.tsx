import { FormEvent, useEffect, useMemo, useState } from 'react';
import AppShell from '../components/AppShell';
import { api, AdminStaff, SchoolResponse, ClassResponse } from '../lib/api';

export default function Admin() {
  const [staff, setStaff] = useState<AdminStaff[]>([]);
  const [schools, setSchools] = useState<SchoolResponse[]>([]);
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);
  const [staffEmail, setStaffEmail] = useState('');
  const [staffPassword, setStaffPassword] = useState('');
  const [staffRole, setStaffRole] = useState<'TEACHER' | 'PRINCIPAL'>('TEACHER');
  const [schoolName, setSchoolName] = useState('');
  const [schoolPrincipalId, setSchoolPrincipalId] = useState('');
  const [selectedSchoolId, setSelectedSchoolId] = useState('');
  const [className, setClassName] = useState('');
  const [teacherId, setTeacherId] = useState('');
  const [selectedClassId, setSelectedClassId] = useState('');
  const [childId, setChildId] = useState('');
  const [pendingPrincipals, setPendingPrincipals] = useState<AdminStaff[]>([]);

  const teachers = useMemo(() => staff.filter(x => x.platformRole === 'TEACHER'), [staff]);
  const selectedSchoolClasses = useMemo(
    () => classes.filter(x => x.schoolId === selectedSchoolId),
    [classes, selectedSchoolId]
  );

  useEffect(() => { load(); }, []);

  async function load() {
    setStatus('');
    try {
      const [s, sc, c, pp] = await Promise.all([api.listAdminStaff(), api.listSchools(), api.listStaffClasses(), api.listPendingPrincipals()]);
      setStaff(s); setSchools(sc); setClasses(c); setPendingPrincipals(pp);
      if (!selectedSchoolId && sc[0]) setSelectedSchoolId(sc[0].id);
      if (!selectedClassId && c[0]) setSelectedClassId(c[0].id);
      if (!teacherId && s.find(x => x.platformRole === 'TEACHER')) setTeacherId(s.find(x => x.platformRole === 'TEACHER')!.id);
    } catch (e:any) { setStatus(e?.message || 'Could not load admin data.'); }
  }

  async function approvePrincipal(id:string) {
    setBusy(true); setStatus('');
    try { await api.approvePrincipal(id); setStatus('Principal approved.'); await load(); }
    catch(e:any){setStatus(e?.message || 'Could not approve principal.');} finally{setBusy(false);}
  }

  async function createStaff(e: FormEvent) {
    e.preventDefault();
    setBusy(true); setStatus('');
    try {
      await api.createAdminStaff(staffEmail, staffPassword, staffRole);
      setStaffEmail(''); setStaffPassword('');
      setStatus(`${staffRole} account created.`);
      await load();
    } catch (e: any) { setStatus(e?.message || 'Could not create staff account.'); }
    finally { setBusy(false); }
  }

  async function createSchool(e: FormEvent) {
    e.preventDefault();
    setBusy(true); setStatus('');
    try {
      const school = await api.createSchool(schoolName, schoolPrincipalId || undefined);
      setSchoolName('');
      setSchoolPrincipalId('');
      setSelectedSchoolId(school.id);
      setStatus(`School “${school.name}” created.`);
      await load();
    } catch (e: any) { setStatus(e?.message || 'Could not create school.'); }
    finally { setBusy(false); }
  }

  async function createClass(e: FormEvent) {
    e.preventDefault();
    if (!selectedSchoolId) return;
    setBusy(true); setStatus('');
    try {
      const created = await api.createSchoolClass(selectedSchoolId, className);
      setClassName('');
      setSelectedClassId(created.id);
      setStatus(`Class “${created.name}” created.`);
      await load();
    } catch (e: any) { setStatus(e?.message || 'Could not create class.'); }
    finally { setBusy(false); }
  }

  async function assignTeacher() {
    if (!selectedClassId || !teacherId) return;
    setBusy(true); setStatus('');
    try {
      await api.assignTeacher(selectedClassId, teacherId);
      setStatus('Teacher assigned to the class.');
    } catch (e: any) { setStatus(e?.message || 'Could not assign teacher.'); }
    finally { setBusy(false); }
  }

  async function enrollChild() {
    if (!selectedClassId || !childId.trim()) return;
    setBusy(true); setStatus('');
    try {
      await api.enrollChildInClass(selectedClassId, childId.trim());
      setChildId('');
      setStatus('Child added to the class.');
    } catch (e: any) { setStatus(e?.message || 'Could not add child to the class.'); }
    finally { setBusy(false); }
  }

  return (
    <AppShell workspace="admin">
      <div className="page-title">
        <div>
          <span className="eyebrow">ADMIN CONSOLE</span>
          <h1>Set up the learning community.</h1>
          <p>Manage staff, schools and classes, manage the platform without entering a teacher workspace.</p>
        </div>
        <span className="workspace-badge">PLATFORM ADMINISTRATION</span>
      </div>

      {status && <div className="form-error" style={{ marginBottom: 20 }}>{status}</div>}

      <div className="dashboard-grid">
        <section className="auth-card"><h2>Pending principal approvals</h2><p>Review school leaders before they receive access.</p>{pendingPrincipals.length===0?<p>No pending principals.</p>:pendingPrincipals.map(x=><div key={x.id} className="list-row"><div><b>{x.email}</b><small>Pending principal registration</small></div><button className="btn primary" disabled={busy} onClick={()=>approvePrincipal(x.id)}>Approve</button></div>)}</section>
        <section className="auth-card">
          <h2>1. Provision staff</h2>
          <form onSubmit={createStaff}>
            <label>Email<input type="email" value={staffEmail} onChange={e => setStaffEmail(e.target.value)} required placeholder="teacher@example.com" /></label>
            <label>Temporary password<input type="password" value={staffPassword} onChange={e => setStaffPassword(e.target.value)} required minLength={12} placeholder="12+ characters" /></label>
            <label>Role<select value={staffRole} onChange={e => setStaffRole(e.target.value as 'TEACHER' | 'PRINCIPAL')}><option value="TEACHER">Teacher</option><option value="PRINCIPAL">Principal</option></select></label>
            <button className="btn primary full" disabled={busy}>Create staff account</button>
          </form>
          <div className="highlight-list" style={{ marginTop: 16 }}>
            {staff.map(x => <div key={x.id}><span className="avatar">S</span><div><b>{x.email}</b><p>{x.platformRole}</p></div></div>)}
          </div>
        </section>

        <section className="auth-card">
          <h2>2. Create a school</h2>
          <form onSubmit={createSchool}>
            <label>School name<input value={schoolName} onChange={e => setSchoolName(e.target.value)} required placeholder="YulMe Learning School" /></label>
            <label>Principal<select value={schoolPrincipalId} onChange={e => setSchoolPrincipalId(e.target.value)}><option value="">Admin-owned school</option>{staff.filter(x => x.platformRole === 'PRINCIPAL').map(x => <option key={x.id} value={x.id}>{x.email}</option>)}</select></label>
            <button className="btn primary full" disabled={busy}>Create school</button>
          </form>
          <label>School<select value={selectedSchoolId} onChange={e => setSelectedSchoolId(e.target.value)}><option value="">Select school</option>{schools.map(x => <option key={x.id} value={x.id}>{x.name}</option>)}</select></label>
        </section>

        <section className="auth-card">
          <h2>3. Create a class</h2>
          <form onSubmit={createClass}>
            <label>Class name<input value={className} onChange={e => setClassName(e.target.value)} required placeholder="Grade 1 · Blue" /></label>
            <button className="btn primary full" disabled={busy || !selectedSchoolId}>Create class</button>
          </form>
          <label>Class<select value={selectedClassId} onChange={e => setSelectedClassId(e.target.value)}><option value="">Select class</option>{selectedSchoolClasses.map(x => <option key={x.id} value={x.id}>{x.name}</option>)}</select></label>
        </section>

        <section className="auth-card">
          <h2>4. Connect the classroom</h2>
          <label>Teacher<select value={teacherId} onChange={e => setTeacherId(e.target.value)}><option value="">Select teacher</option>{teachers.map(x => <option key={x.id} value={x.id}>{x.email}</option>)}</select></label>
          <button className="btn primary full" onClick={assignTeacher} disabled={busy || !selectedClassId || !teacherId}>Assign teacher</button>
          <hr />
          <label>Child ID<input value={childId} onChange={e => setChildId(e.target.value)} placeholder="UUID from parent account" /></label>
          <button className="btn full" onClick={enrollChild} disabled={busy || !selectedClassId || !childId.trim()}>Add child to class</button>
        </section>
      </div>
    </AppShell>
  );
}
