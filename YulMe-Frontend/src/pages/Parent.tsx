import {useEffect, useState} from 'react';
import AppShell from '../components/AppShell';import ProgressRing from '../components/ProgressRing';import {Link,useParams} from 'react-router-dom';
import {api, ChildResponse, ParentInsightResponse} from '../lib/api';

const INSIGHT_ICON: Record<string, string> = {STRENGTH: '💪', WEAKNESS: '🌱', PROGRESS: '📈', RECOMMENDATION: '💡'};

export default function Parent() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeChild, setActiveChild] = useState<ChildResponse | null>(null);
  const [insights, setInsights] = useState<ParentInsightResponse[]>([]);

  useEffect(() => {
    load();
  }, []);

  async function load() {
    setLoading(true);
    setError('');
    try {
      const kids = await api.listChildren();
      if (kids.length === 0) {
        setActiveChild(null);
        setLoading(false);
        return;
      }
      const kid = kids[0];
      setActiveChild(kid);
      // Recompute from the child's current learning state, then show everything generated so far.
      await api.generateParentInsights(kid.id);
      const list = await api.listParentInsights(kid.id);
      setInsights(list);
    } catch (err: any) {
      setError(err?.message || 'Could not load insights right now.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <AppShell workspace="parent" parent>
      <div className="parent-heading">
        <div><span className="eyebrow">PARENT INSIGHT</span><h1>{activeChild ? `${activeChild.displayName}'s learning` : 'Parent insight'}</h1><p>A real, backend-generated snapshot of your child's learning.</p></div>
        <button className="btn ghost" onClick={load}>Refresh</button>
      </div>

      {loading && <p>Loading insights…</p>}
      {!loading && error && <div className="form-error">{error}</div>}
      {!loading && !activeChild && !error && (
        <div className="insight-card"><span className="insight-icon">👶</span><h3>No child yet</h3><p>Add a child from the home screen to see insights here.</p></div>
      )}

      {!loading && activeChild && insights.length === 0 && !error && (
        <div className="insight-card"><span className="insight-icon">💡</span><h3>No insights yet</h3><p>Insights appear here once {activeChild.displayName} completes a learning activity.</p></div>
      )}

      {!loading && activeChild && insights.length > 0 && (
        <div className="parent-grid">
          <section className="parent-overview">
            <div className="child-title">
              <div className="large-avatar">🧒</div>
              <div><h2>{insights[0].headline}</h2><p>{insights[0].detail}</p></div>
            </div>
          </section>
          <aside className="insight-card">
            <span className="insight-icon">{INSIGHT_ICON[insights[0].insightType] || '💡'}</span>
            <span className="tag">{insights[0].insightType.replace('_', ' ')}</span>
            <h3>{insights[0].headline}</h3>
            {insights[0].detail && <p>{insights[0].detail}</p>}
          </aside>
        </div>
      )}

      {!loading && insights.length > 1 && (
        <div className="parent-bottom">
          <section>
            <div className="highlight-list">
              {insights.slice(1).map(insight => (
                <div key={insight.id}>
                  <span>{INSIGHT_ICON[insight.insightType] || '💡'}</span>
                  <div><b>{insight.headline}</b><p>{insight.detail}</p></div>
                  <time>{new Date(insight.occurredAt).toLocaleDateString()}</time>
                </div>
              ))}
            </div>
          </section>
        </div>
      )}
    </AppShell>
  );
}
export function ParentChild(){
  const {id} = useParams<{id:string}>();
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [childRecord,setChildRecord]=useState<ChildResponse|null>(null);
  const [progress,setProgress]=useState<{childId:string;completedActivities:number;learningMinutes:number;currentStreak:number;longestStreak:number;completedPathItems:number;totalPathItems:number}|null>(null);
  const [insights,setInsights]=useState<ParentInsightResponse[]>([]);

  useEffect(()=>{
    async function load(){
      if(!id){setError('Child not found.');setLoading(false);return;}
      setLoading(true);setError('');
      try{
        const kids=await api.listChildren();
        const kid=kids.find(x=>x.id===id);
        if(!kid) throw new Error('Child not found.');
        setChildRecord(kid);
        const [p,i]=await Promise.all([api.getProgress(kid.id),api.listParentInsights(kid.id)]);
        setProgress(p);
        setInsights(i);
      }catch(e:any){setError(e?.message||'Could not load this child.');}
      finally{setLoading(false);}
    }
    load();
  },[id]);

  const pathPercent=progress&&progress.totalPathItems>0?Math.round(progress.completedPathItems/progress.totalPathItems*100):0;
  const primary=insights[0];
  return <AppShell workspace="parent" parent>
    <div className="back-link"><Link to="/parent">← Parent dashboard</Link></div>
    {loading&&<p>Loading learning profile…</p>}
    {!loading&&error&&<div className="form-error">{error}</div>}
    {!loading&&childRecord&&<><div className="child-detail-head"><div className="large-avatar">{childRecord.displayName.slice(0,1).toUpperCase()}</div><div><span className="eyebrow">LEARNING PROFILE</span><h1>{childRecord.displayName}'s learning journey</h1><p>Date of birth: {childRecord.dateOfBirth}</p></div></div>
      <div className="detail-grid">
        <section className="detail-card mastery-detail"><div className="panel-head"><div><h2>Learning progress</h2><p>Progress calculated from this child's real learning records.</p></div><ProgressRing value={pathPercent} size={112}/></div><div className="mastery-bars"><div><span>Activities completed <b>{progress?.completedActivities??0}</b></span></div><div><span>Learning minutes <b>{progress?.learningMinutes??0}</b></span></div><div><span>Path progress <b>{progress?.completedPathItems??0} / {progress?.totalPathItems??0}</b></span></div></div></section>
        <section className="detail-card recommendation"><span className="tag">YULME RECOMMENDS</span>{primary?<><h2>{primary.headline}</h2>{primary.detail&&<p>{primary.detail}</p>}</>:<><h2>No recommendation yet</h2><p>No parent insight has been generated for this child yet.</p></>}</section>
        <section className="detail-card timeline"><div className="panel-head"><div><h2>Recent learning</h2><p>Backend-generated parent insights for this child.</p></div></div>{insights.length===0?<p>No insights yet.</p>:<div className="timeline-list">{insights.slice(0,5).map(x=><div key={x.id}><i>•</i><div><b>{x.headline}</b>{x.detail&&<p>{x.detail}</p>}</div><time>{new Date(x.occurredAt).toLocaleDateString()}</time></div>)}</div>}</section>
        <section className="detail-card privacy"><h2>Your family controls the data.</h2><p>Child records shown here are loaded from the authenticated parent account.</p><Link to="/parent/settings">Review privacy & consent →</Link></section>
      </div></>}
  </AppShell>
}
export function ParentSettings(){
  const [tab,setTab]=useState<'account'|'children'|'privacy'|'notifications'>('account');
  const [children,setChildren]=useState<ChildResponse[]>([]);
  const [saved,setSaved]=useState('');
  const [insights,setInsights]=useState(true);
  const [personalized,setPersonalized]=useState(true);
  const [notifications,setNotifications]=useState(true);

  useEffect(()=>{
    api.listChildren().then(setChildren).catch(()=>setChildren([]));
    setInsights(localStorage.getItem('yulme.setting.insights') !== 'false');
    setPersonalized(localStorage.getItem('yulme.setting.personalized') !== 'false');
    setNotifications(localStorage.getItem('yulme.setting.notifications') !== 'false');
  },[]);

  function saveSettings(){
    localStorage.setItem('yulme.setting.insights',String(insights));
    localStorage.setItem('yulme.setting.personalized',String(personalized));
    localStorage.setItem('yulme.setting.notifications',String(notifications));
    setSaved('Settings saved on this device.');
    window.setTimeout(()=>setSaved(''),2500);
  }

  return <AppShell workspace="parent" parent><div className="page-title"><div><span className="eyebrow">PARENT CONTROLS</span><h1>Settings</h1><p>Manage your account, family preferences and child privacy.</p></div></div>
    <div className="settings-layout">
      <nav className="settings-nav">
        <a className={tab==='account'?'active':''} role="button" onClick={()=>setTab('account')}>Account</a>
        <a className={tab==='children'?'active':''} role="button" onClick={()=>setTab('children')}>Children</a>
        <a className={tab==='privacy'?'active':''} role="button" onClick={()=>setTab('privacy')}>Privacy & consent</a>
        <a className={tab==='notifications'?'active':''} role="button" onClick={()=>setTab('notifications')}>Notifications</a>
      </nav>
      <section className="settings-card">
        {tab==='account' && <><h2>Account</h2><p className="muted">Your parent account details.</p><label>Name<input defaultValue="Parent" /></label><label>Email<input defaultValue="" placeholder="Your account email" /></label><button className="btn primary" onClick={saveSettings}>Save changes</button></>}
        {tab==='children' && <><h2>Your children</h2><p className="muted">Children connected to this parent account.</p>{children.length===0?<div className="safe-note">No children yet. Add one from the home screen.</div>:children.map(k=><div className="setting-row" key={k.id}><div><b>{k.displayName}</b><p>Date of birth: {k.dateOfBirth}</p><p>Child ID: {k.id}</p></div><span>✓ Active</span></div>)}</>}
        {tab==='privacy' && <><h2>Privacy & consent</h2><p className="muted">These controls determine how YulMe uses learning activity for your family.</p><div className="setting-row"><div><b>Learning insights</b><p>Allow YulMe to generate progress summaries from learning evidence.</p></div><input type="checkbox" checked={insights} onChange={e=>setInsights(e.target.checked)}/></div><div className="setting-row"><div><b>Personalized practice</b><p>Allow learning history to guide the next activity recommendation.</p></div><input type="checkbox" checked={personalized} onChange={e=>setPersonalized(e.target.checked)}/></div><div className="safe-note">🛡️ Parent-controlled settings are stored locally until a dedicated consent API is available.</div><button className="btn primary" onClick={saveSettings}>Save privacy choices</button></>}
        {tab==='notifications' && <><h2>Notifications</h2><p className="muted">Choose whether this device should keep parent learning reminders enabled.</p><div className="setting-row"><div><b>Parent learning notifications</b><p>Keep weekly learning reminders enabled for this device.</p></div><input type="checkbox" checked={notifications} onChange={e=>setNotifications(e.target.checked)}/></div><button className="btn primary" onClick={saveSettings}>Save notification settings</button></>}
        {saved && <div className="safe-note" style={{marginTop:16}}>✓ {saved}</div>}
      </section>
    </div>
  </AppShell>
}
