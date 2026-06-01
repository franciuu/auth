import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { auth, ApiError } from '../api.js';
import { styles } from '../styles.js';

// Combined Login / Register page. `mode` is "login" or "register".
export default function AuthPage({ mode }) {
  const isRegister = mode === 'register';
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setSuccess('');
    setBusy(true);
    try {
      if (isRegister) {
        const res = await auth.register({ email, fullName, password });
        setSuccess(res.message || 'Account created. You can now sign in.');
      } else {
        await auth.login({ email, password });
        navigate('/dashboard');
      }
    } catch (err) {
      setError(formatError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={styles.container}>
      <h2>{isRegister ? 'Sign Up' : 'Sign In'}</h2>

      <form onSubmit={handleSubmit} style={styles.form}>
        <input
          style={styles.input}
          type="email"
          placeholder="Email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />

        {isRegister && (
          <input
            style={styles.input}
            type="text"
            placeholder="Full name"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            required
          />
        )}

        <input
          style={styles.input}
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        <button style={styles.button} type="submit" disabled={busy}>
          {busy ? 'Please wait…' : isRegister ? 'Sign Up' : 'Sign In'}
        </button>
      </form>

      {error && <p style={styles.error}>{error}</p>}
      {success && <p style={styles.success}>{success}</p>}

      {isRegister && (
        <p style={styles.hint}>
          Password must be 8+ chars with uppercase, lowercase, a digit and a
          special character (@$!%*?&).
        </p>
      )}

      <p>
        {isRegister ? (
          <>Already have an account? <Link to="/login">Sign In</Link></>
        ) : (
          <>No account? <Link to="/register">Sign Up</Link></>
        )}
      </p>
    </div>
  );
}

function formatError(err) {
  if (err instanceof ApiError) {
    // Surface field-level validation messages (e.g. password strength).
    if (err.data && err.data.errors) {
      return Object.values(err.data.errors).join(' ');
    }
    return err.message;
  }
  return 'Something went wrong. Please try again.';
}
