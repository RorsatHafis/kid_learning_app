import LanguageSwitcher from './LanguageSwitcher';
import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import Icon from './Icon';
import { api, getStoredChildId, getStoredRole } from '../lib/api';

export default function Topbar() {
  const loc = useLocation();
  const [childName, setChildName] = useState('');
  const role = getStoredRole();

  useEffect(() => {
    if (role === 'PARENT') {
      api.listChildren().then(kids => {
        const selected = kids.find(k => k.id === getStoredChildId()) ?? kids[0];
        if (selected) setChildName(selected.displayName);
      }).catch(() => undefined);
    }
  }, [role]);

  let title = 'Keep growing, little learner';
  if (loc.pathname === '/app') title = childName ? `Good afternoon, ${childName}!` : 'Good afternoon!';
  else if (loc.pathname === '/app/learn') title = 'Let’s learn something new';
  else if (loc.pathname === '/app/progress') title = 'Your progress';
  else if (loc.pathname === '/parent') title = 'Parent dashboard';
  else if (loc.pathname === '/parent/settings') title = 'Settings';
  else if (loc.pathname === '/teacher') title = 'Teacher Studio';
  else if (loc.pathname === '/school') title = 'School Leadership';
  else if (loc.pathname === '/admin') title = 'Admin Console';

  return <header className="topbar"><div><div className="mobile-brand"><span className="mobile-brand-mark">Y</span>YulMe</div><p className="eyebrow">{title}</p></div><div className="top-actions"><button className="icon-btn" aria-label="Notifications"><Icon name="bell"/><span className="notif-dot"/></button><div className="top-user"><span className="role-avatar top-avatar">{role === 'ADMIN' ? 'A' : role === 'TEACHER' ? 'T' : role === 'PRINCIPAL' ? 'P' : 'P'}</span><b>{role === 'ADMIN' ? 'Administrator' : role === 'TEACHER' ? 'Teacher' : role === 'PRINCIPAL' ? 'Principal' : 'Parent'}</b><small>Account</small></div></div></header>;
}
