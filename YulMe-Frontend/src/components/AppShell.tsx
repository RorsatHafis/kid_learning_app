import { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import Sidebar from './Sidebar';
import Topbar from './Topbar';

export default function AppShell({ children, parent = false, workspace }: { children: ReactNode; parent?: boolean; workspace?: 'learner'|'parent'|'teacher'|'principal'|'admin' }) {
  const mode = workspace || (parent ? 'parent' : undefined);
  const mobile = mode === 'teacher' ? <><NavLink to="/teacher">Studio</NavLink></> : mode === 'principal' ? <><NavLink to="/school">School</NavLink></> : mode === 'admin' ? <><NavLink to="/admin">Admin</NavLink></> : <><NavLink to="/app">Home</NavLink><NavLink to="/app/learn">Learn</NavLink><NavLink to="/app/progress">Progress</NavLink>{parent && <NavLink to="/parent/settings">Settings</NavLink>}</>;
  return <div className={`app-shell workspace-${mode || 'learner'}`}><Sidebar parent={parent} workspace={mode}/><main className="main"><Topbar/><div className="page">{children}</div><nav className="mobile-nav">{mobile}<button className="mobile-signout" aria-label="Sign out" onClick={()=>{localStorage.clear();window.location.href='/';}}>×</button></nav></main></div>;
}
