import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { auth, api, ApiError } from '../api.js';
import { styles } from '../styles.js';

export default function Dashboard() {
  const navigate = useNavigate();
  const [identity, setIdentity] = useState(auth.getIdentity());
  const [ready, setReady] = useState(!!identity);
  const [profile, setProfile] = useState(null);
  const [users, setUsers] = useState(null);
  const [error, setError] = useState('');

  // On reload the in-memory access token is gone; recover it via silent refresh.
  useEffect(() => {
    if (!identity) {
      auth
        .refresh()
        .then(() => {
          setIdentity(auth.getIdentity());
          setReady(true);
        })
        .catch(() => navigate('/login'));
    }
  }, [identity, navigate]);

  if (!ready) {
    return <div style={styles.container}>Loading…</div>;
  }

  const isAdmin = auth.isAdmin();
  const welcomeColor = isAdmin ? 'red' : 'blue';

  async function handleViewProfile() {
    setError('');
    try {
      setProfile(await api.getProfile());
    } catch (err) {
      setError(messageFor(err));
    }
  }

  async function handleListUsers() {
    setError('');
    try {
      setUsers(await api.listUsers());
    } catch (err) {
      setError(messageFor(err));
    }
  }

  async function handleDisable(userId) {
    setError('');
    try {
      await api.disableUser(userId);
      await handleListUsers(); // refresh the table
    } catch (err) {
      setError(messageFor(err));
    }
  }

  async function handleLogout() {
    await auth.logout();
    navigate('/login');
  }

  return (
    <div style={styles.container}>
      <h2 style={{ color: welcomeColor }}>
        Welcome, {identity?.email} {isAdmin ? '(ADMIN)' : '(USER)'}
      </h2>

      <div style={styles.form}>
        {/* Any authenticated user can view their own profile */}
        <button style={styles.button} onClick={handleViewProfile}>
          View My Profile
        </button>

        {/* Admin-only controls */}
        {isAdmin && (
          <button style={styles.button} onClick={handleListUsers}>
            List All Users
          </button>
        )}

        <button style={styles.button} onClick={handleLogout}>
          Logout
        </button>
      </div>

      {error && <p style={styles.error}>{error}</p>}

      {profile && (
        <div style={{ marginTop: '16px' }}>
          <h3>My Profile</h3>
          <p>ID: {profile.id}</p>
          <p>Email: {profile.email}</p>
          <p>Roles: {profile.roles.join(', ')}</p>
        </div>
      )}

      {isAdmin && users && (
        <div style={{ marginTop: '16px' }}>
          <h3>All Users</h3>
          <table style={styles.table}>
            <thead>
              <tr>
                <th style={styles.cell}>ID</th>
                <th style={styles.cell}>Email</th>
                <th style={styles.cell}>Full Name</th>
                <th style={styles.cell}>Roles</th>
                <th style={styles.cell}>Active</th>
                <th style={styles.cell}>Action</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td style={styles.cell}>{u.id}</td>
                  <td style={styles.cell}>{u.email}</td>
                  <td style={styles.cell}>{u.fullName}</td>
                  <td style={styles.cell}>{u.roles.join(', ')}</td>
                  <td style={styles.cell}>{u.active ? 'Yes' : 'No'}</td>
                  <td style={styles.cell}>
                    <button
                      style={styles.button}
                      disabled={!u.active}
                      onClick={() => handleDisable(u.id)}
                    >
                      Disable User
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function messageFor(err) {
  if (err instanceof ApiError) {
    if (err.status === 403) return 'Access denied: you do not have permission for this action.';
    return err.message;
  }
  return 'Something went wrong.';
}
