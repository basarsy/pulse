import { useAuth } from '../context/auth';
import { useTopic } from '../context/live';
import { useNotifications } from '../context/notifications';
import { vehicleApi } from '../api/endpoints';
import { useAsync } from '../hooks/useAsync';
import type { Campaign, Command, Vehicle } from '../api/types';
import { label } from '../utils/format';

const FINAL_COMMAND_STATES = ['COMPLETED', 'FAILED', 'TIMED_OUT', 'CANCELLED'];

function useCommandNotifications(destination: string | null) {
  const { addNotification } = useNotifications();
  useTopic<Command>(destination, command => {
    if (FINAL_COMMAND_STATES.includes(command.status)) {
      addNotification(`${label(command.type)} ${label(command.status).toLowerCase()}`,
        command.status === 'COMPLETED' ? 'success' : command.status === 'CANCELLED' ? 'warning' : 'error');
    }
  });
}

function VehicleCommandNotifications({ vehicle }: { vehicle: Vehicle }) {
  useCommandNotifications(`/topic/vehicles/${vehicle.id}/commands`);
  return null;
}

function OwnerNotifications() {
  // Owners may only subscribe to topics of their own vehicles.
  const { data: vehicles } = useAsync(vehicleApi.mine, [], { pollMs: 15000 });
  return <>{vehicles?.map(v => <VehicleCommandNotifications key={v.id} vehicle={v} />)}</>;
}

function AdminNotifications() {
  const { addNotification } = useNotifications();
  useCommandNotifications('/topic/vehicles/*/commands');
  useTopic<Campaign>('/topic/rollouts/*', campaign => {
    if (campaign.status === 'PAUSED' || campaign.status === 'COMPLETED') {
      addNotification(`Rollout ${campaign.softwareVersion.versionLabel} (${campaign.targetModel}) ${campaign.status.toLowerCase()}`,
        campaign.status === 'COMPLETED' ? 'success' : 'warning');
    }
  });
  return null;
}

/** Turns live server events into toast notifications for the signed-in user. */
export default function LiveNotifications() {
  const { isAdmin } = useAuth();
  return isAdmin ? <AdminNotifications /> : <OwnerNotifications />;
}
