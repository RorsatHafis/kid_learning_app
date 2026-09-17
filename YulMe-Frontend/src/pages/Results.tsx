import {useEffect, useState} from 'react';
import {Link, useLocation, useNavigate} from 'react-router-dom';
import ProgressRing from '../components/ProgressRing';
import AppShell from '../components/AppShell';
import {AdaptiveRecommendationResponse, AnswerRecordResponse, api, AttemptResponse, SmartReviewCheckResponse} from '../lib/api';

type ResultsState = {
  attempt: AttemptResponse;
  adaptiveNext?: AdaptiveRecommendationResponse;
  smartReview?: SmartReviewCheckResponse;
  enrollmentId?: string;
};

const DIRECTION_COPY: Record<string, {headline: string; body: string}> = {
  REINFORCEMENT: {headline: "Let's practice that a bit more", body: 'A little more practice on this skill will help it stick.'},
  CHALLENGE: {headline: "You're ready for something harder!", body: "You've shown strong evidence here, so YulMe picked a tougher next step."},
  CONTINUE: {headline: 'Keep up the steady progress', body: "Let's keep moving through the path at this pace."},
};

export default function Results() {
  const location = useLocation();
  const nav = useNavigate();
  const state = location.state as ResultsState | undefined;
  const [answers, setAnswers] = useState<AnswerRecordResponse[] | null>(null);

  useEffect(() => {
    if (state?.attempt) {
      api.listAnswers(state.attempt.id).then(setAnswers).catch(() => setAnswers(null));
    }
  }, [state?.attempt.id]);

  if (!state?.attempt) {
    return (
      <AppShell>
        <div className="results-page">
          <h1>No recent activity to show</h1>
          <p className="lead">Head back home to start (or continue) a learning activity.</p>
          <div className="result-actions"><Link className="btn primary big" to="/app">Go home</Link></div>
        </div>
      </AppShell>
    );
  }

  const {attempt, adaptiveNext, smartReview, enrollmentId} = state;
  const correctCount = answers?.filter(a => a.correct).length ?? null;
  const total = answers?.length ?? null;
  const scoreDisplay = attempt.score !== null ? `${Number(attempt.score).toFixed(0)}%` : '—';
  const direction = adaptiveNext ? DIRECTION_COPY[adaptiveNext.direction] : undefined;

  function continueToAdaptiveNext() {
    if (!adaptiveNext) return;
    nav(`/app/activity/${adaptiveNext.activityVersionId}?item=${adaptiveNext.insertedLearningPathItemId}${enrollmentId ? `&enrollment=${enrollmentId}` : ''}`);
  }

  return (
    <AppShell>
      <div className="results-page">
        <div className="confetti">✦　•　✦　•　✦</div>
        <span className="eyebrow">ACTIVITY COMPLETE</span>
        <h1>Nice work! 🎉</h1>
        <p className="lead">Here's what YulMe saw in your answers.</p>

        <div className="result-main">
          <ProgressRing value={attempt.score !== null ? Number(attempt.score) : 0} size={150} label="score" />
          <div>
            <b>Your result</b>
            <span>{total !== null ? `${correctCount} of ${total} correct` : 'Score calculated by the server'}</span>
            <small>Score: {scoreDisplay}</small>
          </div>
        </div>

        {direction && (
          <div className="insight-card" style={{textAlign: 'left', margin: '0 auto 22px', maxWidth: 530}}>
            <span className="insight-icon">🧭</span>
            <span className="tag">WHAT'S NEXT</span>
            <h3>{direction.headline}</h3>
            <p>{direction.body}</p>
            <button className="btn primary" onClick={continueToAdaptiveNext}>Continue learning →</button>
          </div>
        )}

        {smartReview?.flaggedForReview && (
          <div className="insight-card" style={{textAlign: 'left', margin: '0 auto 22px', maxWidth: 530, background: 'linear-gradient(145deg,#fff0f5,#fff)'}}>
            <span className="insight-icon">🔁</span>
            <span className="tag">SMART REVIEW</span>
            <h3>This skill has been scheduled for review</h3>
            <p>YulMe noticed this needs a bit more practice, and will bring it back at the right time.</p>
          </div>
        )}

        <div className="result-actions">
          {!adaptiveNext && <Link className="btn primary big" to="/app">Keep learning →</Link>}
          <Link className="btn ghost big" to="/parent">See parent insights</Link>
        </div>
        <p className="encourage-line">"Every small step makes your brain stronger." 💙</p>
      </div>
    </AppShell>
  );
}
