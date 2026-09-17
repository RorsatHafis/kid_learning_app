import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Logo from '../components/Logo';
import { api, clearSession, PlatformRole, setStoredChildId } from '../lib/api';

const ROLE_COPY: Record<PlatformRole, { title: string; eyebrow: string; description: string; path: string }> = {
  PARENT: { title: 'Parent sign in', eyebrow: 'FAMILY LEARNING', description: "Sign in to manage your family's learning journey.", path: '/login/parent' },
  TEACHER: { title: 'Teacher sign in', eyebrow: 'TEACHING WORKSPACE', description: 'Sign in to create lessons, activities, questions and curriculum.', path: '/login/teacher' },
  PRINCIPAL: { title: 'Principal sign in', eyebrow: 'SCHOOL LEADERSHIP', description: 'Sign in to manage your school learning community.', path: '/login/principal' },
  ADMIN: { title: 'Admin sign in', eyebrow: 'PLATFORM ADMINISTRATION', description: 'Sign in to manage platform-level staff, schools and content.', path: '/login/admin' },
};

export function LoginChooser() {
  return <div className="auth-page role-login-page">
    <div className="auth-art"><img src="/assets/yulme-cheetah-transparent.png" alt="YulMe learning character" /><div><b>One platform. Clear roles.</b><span>Everyone gets the workspace designed for their responsibility.</span></div></div>
    <div className="auth-panel"><Logo/><div className="auth-card role-chooser">
      <span className="eyebrow">SIGN IN</span><h1>Choose your workspace.</h1><p>Use the sign-in area that matches your YulMe account.</p>
      <div className="role-grid">
        {(['PARENT','TEACHER','PRINCIPAL','ADMIN'] as PlatformRole[]).map(role => <Link className="role-card" key={role} to={ROLE_COPY[role].path}><span className="role-card-mark">{role[0]}</span><div><b>{ROLE_COPY[role].title.replace(' sign in','')}</b><small>{ROLE_COPY[role].eyebrow}</small></div><span className="role-arrow">→</span></Link>)}
      </div>
      <p className="auth-switch">New family? <Link to="/register">Create a parent account</Link></p><p className="auth-switch">School leader? <Link to="/register/principal">Request principal access</Link></p>
    </div></div></div>;
}

export function Login({ expectedRole }: { expectedRole: PlatformRole }) {
  const nav = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const copy = ROLE_COPY[expectedRole];

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!email || !password) { setError('Please enter your email and password.'); return; }
    setError(''); setLoading(true);
    try {
      const auth = await api.login(email, password);
      if (auth.platformRole !== expectedRole) {
        clearSession();
        setError(`This account is for ${ROLE_COPY[auth.platformRole].title.replace(' sign in','')}. Use that workspace instead.`);
        return;
      }
      nav(expectedRole === 'PARENT' ? '/app' : expectedRole === 'TEACHER' ? '/teacher' : expectedRole === 'PRINCIPAL' ? '/school' : '/admin');
    } catch (err: any) { setError(err?.message || 'Could not sign in. Please check your details and try again.'); }
    finally { setLoading(false); }
  }

  return <div className="auth-page role-login-page">
    <div className="auth-art"><img src="/assets/yulme-cheetah-transparent.png" alt="YulMe learning character" /><div><b>{expectedRole === 'PARENT' ? 'Every child learns differently.' : 'A clear workspace for every role.'}</b><span>{expectedRole === 'PARENT' ? "We're here to understand." : copy.description}</span></div></div>
    <div className="auth-panel"><Logo/><div className="auth-card">
      <Link className="back-link" to="/login">← All workspaces</Link>
      <span className="eyebrow">{copy.eyebrow}</span><h1>{copy.title}.</h1><p>{copy.description}</p>
      <form onSubmit={submit}>
        <label>Email<input value={email} onChange={e => setEmail(e.target.value)} type="email" autoComplete="email" required /></label>
        <label>Password<div className="password"><input value={password} onChange={e => setPassword(e.target.value)} type={showPassword ? 'text' : 'password'} autoComplete="current-password" required /><button type="button" onClick={() => setShowPassword(v => !v)}>{showPassword ? 'Hide' : 'Show'}</button></div></label>
        {error && <div className="form-error">{error}</div>}
        <button className="btn primary full" disabled={loading}>{loading ? 'Signing in…' : 'Sign in'} <span>→</span></button>
      </form>
      {expectedRole === 'PARENT' && <p className="auth-switch">New to YulMe? <Link to="/register">Create an account</Link></p>}
      {expectedRole !== 'PARENT' && <div className="safe-note">Staff accounts are provisioned by an administrator.</div>}
    </div></div></div>;
}

export function PrincipalRegister(){
  const [email,setEmail]=useState(''); const [password,setPassword]=useState(''); const [name,setName]=useState(''); const [message,setMessage]=useState(''); const [loading,setLoading]=useState(false);
  async function submit(e:FormEvent){e.preventDefault();setLoading(true);setMessage('');try{await api.register(email,password,name,'PRINCIPAL');setMessage('Registration received. Your principal account is pending administrator approval.');}catch(err:any){setMessage(err?.message||'Could not submit registration.');}finally{setLoading(false);}}
  return <div className="auth-page"><div className="auth-art"><img src="/assets/yulme-cheetah-transparent.png" alt="YulMe learning character"/><div><b>Lead a learning community.</b><span>Principal registrations are reviewed before school access is activated.</span></div></div><div className="auth-panel"><Logo/><div className="auth-card"><span className="eyebrow">PRINCIPAL REGISTRATION</span><h1>Request school access.</h1><p>Register your principal account. An administrator will review and approve it before you can sign in.</p><form onSubmit={submit}><label>Name<input value={name} onChange={e=>setName(e.target.value)} required/></label><label>Email<input value={email} onChange={e=>setEmail(e.target.value)} type="email" required/></label><label>Password<input value={password} onChange={e=>setPassword(e.target.value)} type="password" minLength={12} required/></label>{message&&<div className="safe-note">{message}</div>}<button className="btn primary full" disabled={loading}>{loading?'Submitting…':'Submit registration'} <span>→</span></button></form><p className="auth-switch"><Link to="/login/principal">Already approved? Sign in</Link></p></div></div></div>;
}

export function Register() {
  const nav = useNavigate();
  const [step, setStep] = useState(1); const [error, setError] = useState(''); const [loading, setLoading] = useState(false);
  const [familyName, setFamilyName] = useState(''); const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const [childName, setChildName] = useState(''); const [age, setAge] = useState('6');
  function continueToChildStep(e: FormEvent){e.preventDefault();if(!email||!password){setError('Please fill in your email and password.');return;}setError('');setStep(2);}
  async function createFamilyAndChild(e: FormEvent){e.preventDefault();if(!childName){setError("Please tell us your child's first name.");return;}setError('');setLoading(true);try{await api.register(email,password,familyName||undefined);const child=await api.createChild(childName,dateOfBirthForAge(Number(age)));setStoredChildId(child.id);const curricula=await api.listCurricula();if(curricula.length>0)await api.enroll(child.id,curricula[0].id);nav('/app');}catch(err:any){setError(err?.message||'Something went wrong creating your family. Please try again.');}finally{setLoading(false);}}
  return <div className="auth-page"><div className="auth-art warm"><img src="/assets/yulme-cheetah-transparent.png" alt="YulMe learning character"/><div><b>Start small. Grow with confidence.</b><span>Your family's learning journey starts here.</span></div></div><div className="auth-panel"><Logo/><div className="auth-card"><div className="stepper"><span className={step>=1?'on':''}>1</span><i/><span className={step>=2?'on':''}>2</span></div><span className="eyebrow">CREATE YOUR FAMILY</span><h1>{step===1?'Welcome to YulMe.':'Meet your little learner.'}</h1><p>{step===1?'Create a parent account to get started.':'A few details help us shape the first learning path.'}</p><form onSubmit={step===1?continueToChildStep:createFamilyAndChild}>{step===1?<><label>Family name<input value={familyName} onChange={e=>setFamilyName(e.target.value)} placeholder="e.g. The Alex Family"/></label><label>Email<input value={email} onChange={e=>setEmail(e.target.value)} type="email" placeholder="you@example.com" required/></label><label>Password<input value={password} onChange={e=>setPassword(e.target.value)} type="password" placeholder="At least 8 characters" required/></label></>:<><label>Child's first name<input value={childName} onChange={e=>setChildName(e.target.value)} placeholder="e.g. Mina" required/></label><label>Age<select value={age} onChange={e=>setAge(e.target.value)}><option>3</option><option>4</option><option>5</option><option>6</option><option>7</option><option>8</option></select></label><label className="check consent"><input type="checkbox" required/> I'm a parent or authorized guardian.</label></>}{error&&<div className="form-error">{error}</div>}<button className="btn primary full" disabled={loading}>{loading?'Setting things up…':step===1?'Continue':'Create my family'} <span>→</span></button></form><p className="auth-switch">Already have an account? <Link to="/login/parent">Sign in</Link></p><div className="safe-note">Child-first privacy · You stay in control</div></div></div></div>;
}

function dateOfBirthForAge(age:number):string{return `${new Date().getFullYear()-age}-01-02`;}
