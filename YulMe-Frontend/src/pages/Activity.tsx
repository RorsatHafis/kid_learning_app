import {useEffect, useState} from 'react';
import {Link, useNavigate, useParams, useSearchParams} from 'react-router-dom';
import ProgressRing from '../components/ProgressRing';
import {ActivityVersionDetail, api, getStoredChildId} from '../lib/api';

type Phase = 'loading' | 'playing' | 'finishing' | 'error';

export default function Activity() {
  const {id: activityVersionId} = useParams();
  const [params] = useSearchParams();
  const learningPathItemId = params.get('item');
  const enrollmentId = params.get('enrollment');
  const nav = useNavigate();

  const [phase, setPhase] = useState<Phase>('loading');
  const [error, setError] = useState('');
  const [activity, setActivity] = useState<ActivityVersionDetail | null>(null);
  const [attemptId, setAttemptId] = useState<string | null>(null);

  const [q, setQ] = useState(0);
  const [selected, setSelected] = useState('');
  const [checked, setChecked] = useState(false);
  const [correct, setCorrect] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [questionStartedAt, setQuestionStartedAt] = useState(Date.now());

  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!activityVersionId) return;
      const childId = getStoredChildId();
      if (!childId) {
        setError('No child selected. Please return to the home screen.');
        setPhase('error');
        return;
      }
      try {
        const [detail, attempt] = await Promise.all([
          api.getActivityVersion(activityVersionId),
          api.startAttempt(childId, activityVersionId, learningPathItemId),
        ]);
        if (cancelled) return;
        setActivity(detail);
        setAttemptId(attempt.id);
        setQuestionStartedAt(Date.now());
        setPhase('playing');
      } catch (err: any) {
        if (cancelled) return;
        setError(err?.message || 'Could not load this activity.');
        setPhase('error');
      }
    }
    load();
    return () => { cancelled = true; };
  }, [activityVersionId]);

  if (phase === 'loading') {
    return <div className="player"><main className="question-stage"><div className="question-card"><h1>Loading your activity…</h1></div></main></div>;
  }
  if (phase === 'error' || !activity || !attemptId) {
    return (
      <div className="player">
        <main className="question-stage">
          <div className="question-card">
            <h1>Couldn't load this activity</h1>
            <p>{error}</p>
            <Link className="btn primary big next-btn" to="/app">Back home</Link>
          </div>
        </main>
      </div>
    );
  }

  const items = activity.items;
  const question = items[q];
  const hasOptions = question.options.length > 0;

  async function checkAnswer() {
    if (!selected || submitting) return;
    setSubmitting(true);
    try {
      const timeSpentSeconds = Math.max(1, Math.round((Date.now() - questionStartedAt) / 1000));
      const result = await api.submitAnswer(attemptId!, question.questionVersionId, selected, timeSpentSeconds);
      setCorrect(result.correct);
      setChecked(true);
    } catch (err: any) {
      setError(err?.message || 'Could not submit your answer.');
    } finally {
      setSubmitting(false);
    }
  }

  async function next() {
    if (!checked) {
      await checkAnswer();
      return;
    }
    if (q < items.length - 1) {
      setQ(q + 1);
      setSelected('');
      setChecked(false);
      setQuestionStartedAt(Date.now());
      return;
    }
    // Last question - complete the attempt and let the backend decide what's next.
    setPhase('finishing');
    try {
      const completed = await api.completeAttempt(attemptId!);
      const [adaptiveNext, smartReview] = await Promise.all([
        api.getAdaptiveNext(attemptId!),
        api.getSmartReviewCheck(attemptId!),
      ]);
      nav('/app/results', {state: {attempt: completed, adaptiveNext, smartReview, enrollmentId}});
    } catch (err: any) {
      setError(err?.message || 'Could not complete this activity.');
      setPhase('error');
    }
  }

  return (
    <div className="player">
      <header className="player-top">
        <Link className="player-logo" to="/app">YulMe</Link>
        <div className="player-progress">
          <span>{activity.title} · Question {q + 1} of {items.length}</span>
          <div><i style={{width: `${((q + (checked ? 1 : 0)) / items.length) * 100}%`}} /></div>
        </div>
        <Link to="/app" className="close-player">×</Link>
      </header>
      <main className="question-stage">
        <div className="question-card">
          <div className="question-kicker">{q === 0 ? "LET'S PLAY" : "YOU'RE DOING GREAT"} ✨</div>
          <h1>{question.prompt}</h1>
          {hasOptions ? (
            <div className="answer-grid">
              {question.options.map(o => (
                <button
                  key={o.id}
                  className={`answer ${selected === o.id ? 'selected' : ''} ${checked && selected === o.id && correct ? 'correct' : ''} ${checked && selected === o.id && !correct ? 'wrong' : ''}`}
                  onClick={() => !checked && setSelected(o.id)}
                  disabled={checked}
                >
                  {o.label}
                </button>
              ))}
            </div>
          ) : (
            <div className="answer-grid" style={{gridTemplateColumns: '1fr'}}>
              <input
                className={checked ? (correct ? 'answer correct' : 'answer wrong') : 'answer'}
                style={{textAlign: 'center'}}
                inputMode="numeric"
                value={selected}
                disabled={checked}
                onChange={e => setSelected(e.target.value)}
                placeholder="Type your answer"
              />
            </div>
          )}
          {checked && (
            <div className={`feedback ${correct ? 'good' : 'try'}`}>
              <span>{correct ? '🎉' : '💡'}</span>
              <div><b>{correct ? 'Great job!' : 'Nice try!'}</b><p>{correct ? 'You found it! Keep going.' : "That's okay. Let's look again and learn from it."}</p></div>
            </div>
          )}
          <button className="btn primary big next-btn" disabled={!selected || submitting} onClick={next}>
            {submitting || phase === 'finishing' ? 'Please wait…' : checked ? (q === items.length - 1 ? 'Finish activity' : 'Next question') : 'Check answer'} <span>→</span>
          </button>
        </div>
        <aside className="player-side">
          <ProgressRing value={Math.round((q / items.length) * 100)} label="activity" size={124} />
          <div className="player-tip">🦋<b>Remember</b><p>You can try again. Mistakes are part of learning.</p></div>
        </aside>
      </main>
    </div>
  );
}
