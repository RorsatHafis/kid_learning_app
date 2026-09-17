import { NavLink, useNavigate } from 'react-router-dom';
import Icon from './Icon';
import Logo from './Logo';
import { clearSession, getStoredRole, PlatformRole } from '../lib/api';

const learnerLinks = [['/app','home','Home'],['/app/learn','book','Learn'],['/app/progress','chart','My Progress'],['/parent','heart','Parent Insight']];

export default function Sidebar({ parent = false, workspace }: { parent?: boolean; workspace?: 'learner'|'parent'|'teacher'|'principal'|'admin' }) {
  const nav = useNavigate();
  const role = getStoredRole() as PlatformRole | null;
  const mode = workspace || (parent ? 'parent' : role === 'ADMIN' ? 'admin' : role === 'PRINCIPAL' ? 'principal' : role === 'TEACHER' ? 'teacher' : 'learner');
  const links = mode === 'teacher' ? [['/teacher','home','Dashboard'],['/teacher/studio','book','Teacher Studio']] : mode === 'principal' ? [['/school','school','Dashboard'],['/school/classes','school','Classes']] : mode === 'admin' ? [['/admin','home','Dashboard'],['/admin/console','settings','Admin Console']] : parent ? learnerLinks : learnerLinks;
  const subtitle = mode === 'admin' ? 'Platform administration' : mode === 'principal' ? 'School leadership' : mode === 'teacher' ? 'Teaching workspace' : parent ? 'Parent view' : 'Learning workspace';
  function signOut(){ clearSession(); nav('/'); }
  return <aside className="sidebar"><Logo/><div className="profile-mini"><div className="avatar role-avatar">{mode === 'admin' ? 'A' : mode === 'principal' ? 'P' : mode === 'teacher' ? 'T' : parent ? 'P' : 'L'}</div><div><b>{mode === 'admin' ? 'Administrator' : mode === 'principal' ? 'Principal' : mode === 'teacher' ? 'Teacher' : parent ? 'Parent' : 'Learner'}</b><span>{subtitle}</span></div></div><nav>{links.map(([to,icon,label])=><NavLink key={to} to={to} className={({isActive})=>isActive?'active':''}><Icon name={icon}/><span>{label}</span></NavLink>)}</nav><div className="side-bottom">{mode === 'parent' && <NavLink to="/parent/settings"><Icon name="settings"/><span>Settings</span></NavLink>}<button className="logout" onClick={signOut}><Icon name="logout"/><span>Sign out</span></button></div></aside>
}
