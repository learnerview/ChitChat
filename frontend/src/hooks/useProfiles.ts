import { useCallback, useState } from 'react';
import type { UserProfile } from '../api/types';

/** Cache of userId -> profile, used for display names and avatars. */
export function useProfiles() {
  const [profiles, setProfiles] = useState<Record<string, UserProfile>>({});

  const mergeProfiles = useCallback((users: UserProfile[]) => {
    setProfiles(prev => {
      const next = { ...prev };
      for (const user of users) next[user.id] = user;
      return next;
    });
  }, []);

  return { profiles, mergeProfiles };
}
