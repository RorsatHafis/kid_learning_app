import { Link } from 'react-router-dom';

export default function Logo({ light = false }: { light?: boolean }) {
  return <Link className="logo" to="/" aria-label="YulMe home">
    <span className={`logo-mark ${light ? 'light' : ''}`} aria-hidden="true">
      <span className="logo-person"><span className="logo-head"/><span className="logo-body"/></span>
      <i/><i/><i/>
    </span>
    <span className={`logo-word ${light ? 'light' : ''}`}><b>Yul</b><strong>Me</strong></span>
  </Link>;
}
