import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/auth';
import AuthShell from '../components/AuthShell';
import Button from '../components/Button';
import styles from '../components/AuthShell.module.css';

export default function RegisterPage() {
  const [formData, setFormData] = useState({ email: '', password: '', fullName: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const { register } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      // Registration returns a token pair, so the new user is signed in straight away.
      await register(formData.fullName, formData.email, formData.password);
      navigate('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell title="Create an account" footer={<>Already have one? <Link to="/login">Sign in</Link></>}>
      <form className={styles.form} onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="fullName">Full name</label>
          <input id="fullName" type="text" required autoComplete="name" className="input" value={formData.fullName} onChange={e => setFormData({ ...formData, fullName: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" type="email" required autoComplete="email" className="input" value={formData.email} onChange={e => setFormData({ ...formData, email: e.target.value })} />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" required minLength={6} autoComplete="new-password" className="input" value={formData.password} onChange={e => setFormData({ ...formData, password: e.target.value })} />
        </div>
        {error && <p className="error" role="alert">{error}</p>}
        <Button type="submit" className={styles.submit} isLoading={loading}>Create account</Button>
      </form>
    </AuthShell>
  );
}
