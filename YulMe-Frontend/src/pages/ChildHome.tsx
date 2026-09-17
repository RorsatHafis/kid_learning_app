import {FormEvent, useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import AppShell from '../components/AppShell';
import {
  ActivityVersionDetail,
  api,
  ApiError,
  ChildResponse,
  EnrollmentResponse,
  getStoredChildId,
  LearningPathItemResponse,
  setStoredChildId,
} from '../lib/api';

type LoadState =
  | {phase: 'loading'}
  | {phase: 'no-child'}
  | {phase: 'no-curriculum'}
  | {phase: 'caught-up'; child: ChildResponse}
  | {phase: 'ready'; child: ChildResponse; enrollment: EnrollmentResponse; item: LearningPathItemResponse; activity: ActivityVersionDetail}
  | {phase: 'error'; message: string};

export default function ChildHome() {
  const nav = useNavigate();
  const [state, setState] = useState<LoadState>({phase: 'loading'});

  useEffect(() => {
    load();
  }, []);

  async function load() {
    setState({phase: 'loading'});
    try {
      const children = await api.listChildren();
      const storedId = getStoredChildId();
      const child = children.find(c => c.id === storedId) ?? children[0];

      if (!child) {
        setState({phase: 'no-child'});
        return;
      }
      setStoredChildId(child.id);

      const curricula = await api.listCurricula();
      if (curricula.length === 0) {
        setState({phase: 'no-curriculum'});
        return;
      }

      const enrollment = await api.enroll(child.id, curricula[0].id);

      let item: LearningPathItemResponse;
      try {
        item = await api.getNextActivity(enrollment.id);
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) {
          setState({phase: 'caught-up', child});
          return;
        }
        throw err;
      }

      const activity = await api.getActivityVersion(item.activityVersionId);
      setState({phase: 'ready', child, enrollment, item, activity});
    } catch (err: any) {
      setState({phase: 'error', message: err?.message || 'Something went wrong loading your learning path.'});
    }
  }

  function startActivity() {
    if (state.phase !== 'ready') return;
    nav(`/app/activity/${state.activity.activityVersionId}?item=${state.item.id}&enrollment=${state.enrollment.id}`);
  }

  if (state.phase === 'loading') {
    return <AppShell><div className="page-title"><div><h1>Loading your adventure…</h1></div></div></AppShell>;
  }

  if (state.phase === 'no-child') {
    return <AppShell><AddChildCard onAdded={load} /></AppShell>;
  }

  if (state.phase === 'no-curriculum') {
    return (
      <AppShell>
        <div className="page-title"><div><h1>Nothing to learn yet</h1><p>No curriculum has been published for your child's age yet. Please check back soon.</p></div></div>
      </AppShell>
    );
  }

  if (state.phase === 'error') {
    return (
      <AppShell>
        <div className="page-title"><div><h1>Something went wrong</h1><p>{state.message}</p></div></div>
        <button className="btn primary" onClick={load}>Try again</button>
      </AppShell>
    );
  }

  if (state.phase === 'caught-up') {
    return (
      <AppShell>
        <div className="welcome"><div><span className="eyebrow">GREAT WORK</span><h1>Hi, {state.child.displayName}! <span>🎉</span></h1><p>You're all caught up - there's nothing waiting for you right now.</p></div></div>
      </AppShell>
    );
  }

  const {child, activity} = state;
  return (
    <AppShell>
      <div className="welcome">
        <div><span className="eyebrow">READY WHEN YOU ARE</span><h1>Hi, {child.displayName}! <span>👋</span></h1><p>Let's keep learning.</p></div>
      </div>
      <div className="dashboard-grid">
        <section className="continue-card">
          <div className="continue-illustration"><span>+</span><div>★</div></div>
          <div className="continue-copy">
            <span className="tag">CONTINUE LEARNING</span>
            <h2>{activity.title}</h2>
            <p>{activity.instructions || `${activity.items.length} question${activity.items.length === 1 ? '' : 's'} ready for you.`}</p>
            <button className="btn primary" onClick={startActivity}>Start <span>→</span></button>
          </div>
        </section>
      </div>
    </AppShell>
  );
}

function AddChildCard({onAdded}: {onAdded: () => void}) {
  const [name, setName] = useState('');
  const [age, setAge] = useState('6');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!name) {
      setError("Please enter your child's first name.");
      return;
    }
    setError('');
    setLoading(true);
    try {
      const year = new Date().getFullYear() - Number(age);
      const child = await api.createChild(name, `${year}-01-02`);
      setStoredChildId(child.id);
      onAdded();
    } catch (err: any) {
      setError(err?.message || 'Could not add your child. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-card" style={{margin: '40px auto', maxWidth: 420}}>
      <span className="eyebrow">ADD YOUR CHILD</span>
      <h1>Who's learning today?</h1>
      <p>Add your child to start their first adventure.</p>
      <form onSubmit={submit}>
        <label>Child's first name<input value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Mina" /></label>
        <label>Age<select value={age} onChange={e => setAge(e.target.value)}>
          <option>3</option><option>4</option><option>5</option><option>6</option><option>7</option><option>8</option>
        </select></label>
        {error && <div className="form-error">{error}</div>}
        <button className="btn primary full" disabled={loading}>{loading ? 'Adding…' : 'Add child'} <span>→</span></button>
      </form>
    </div>
  );
}
