import { CircleCheck, CircleDot, CirclePause, Circle } from 'lucide-react';
import type { CampaignStatus, RolloutStage } from '../api/types';
import styles from './StageTrack.module.css';

type NodeState = 'done' | 'active' | 'paused' | 'waiting';

function nodeState(stage: RolloutStage, index: number, current: number, campaign: CampaignStatus): NodeState {
  if (stage.status === 'COMPLETED') return 'done';
  if (index === current && campaign === 'PAUSED') return 'paused';
  if (stage.status === 'IN_PROGRESS') return 'active';
  return 'waiting';
}

const ICON = { done: CircleCheck, active: CircleDot, paused: CirclePause, waiting: Circle };
const TEXT = { done: 'Done', active: 'Running', paused: 'Paused', waiting: 'Waiting' };

/** A rollout's stages as a timeline: each stage's share of the fleet and where the rollout is. */
export default function StageTrack({ stages, currentStageIndex, campaignStatus }: {
  stages: RolloutStage[];
  currentStageIndex: number;
  campaignStatus: CampaignStatus;
}) {
  return (
    <ol className={styles.track} aria-label="Rollout stages">
      {stages.map((stage, index) => {
        const state = nodeState(stage, index, currentStageIndex, campaignStatus);
        const Icon = ICON[state];
        return (
          <li key={stage.id} className={`${styles.stage} ${styles[state]}`}>
            <div className={styles.marker}>
              <Icon size={18} strokeWidth={1.75} aria-hidden />
              {index < stages.length - 1 && <span className={styles.line} aria-hidden />}
            </div>
            <div className={styles.text}>
              <span className={`${styles.pct} mono`}>{stage.percentage}%</span>
              <span className={styles.state}>Stage {index + 1} · {TEXT[state]}</span>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
