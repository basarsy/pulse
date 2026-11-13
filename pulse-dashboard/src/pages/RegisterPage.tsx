import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import Button from '../components/Button';

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
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'radial-gradient(circle at top right, rgba(59, 130, 246, 0.2), transparent 40%), var(--color-bg-primary)'
    }}>
      <div className="glass-panel" style={{ width: '100%', maxWidth: '400px', padding: '2.5rem' }}>
        <h2 style={{ textAlign: 'center', marginBottom: '2rem' }}>Join <span className="text-gradient">Pulse</span></h2>
        
        {error && <div style={{ color: 'var(--color-danger)', marginBottom: '1rem', textAlign: 'center' }}>{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Full Name</label>
            <input type="text" required className="form-input" value={formData.fullName} onChange={e => setFormData({...formData, fullName: e.target.value})} />
          </div>
          <div className="form-group">
            <label className="form-label">Email</label>
            <input type="email" required className="form-input" value={formData.email} onChange={e => setFormData({...formData, email: e.target.value})} />
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <input type="password" required className="form-input" value={formData.password} onChange={e => setFormData({...formData, password: e.target.value})} />
          </div>
          <Button type="submit" style={{ width: '100%' }} isLoading={loading}>Register</Button>
        </form>
        
        <div style={{ marginTop: '1.5rem', textAlign: 'center', fontSize: '0.875rem' }}>
          Already have an account? <Link to="/login">Sign in</Link>
        </div>
      </div>
    </div>
  );
}
