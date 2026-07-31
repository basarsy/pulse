import React from 'react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
}

export default function Button({ 
  children, 
  variant = 'primary', 
  size = 'md', 
  isLoading, 
  className = '', 
  ...props 
}: ButtonProps) {
  const baseStyles = 'inline-flex items-center justify-center rounded-md font-medium transition-all focus:outline-none';
  
  // Hand-crafted CSS classes mapping to our theme
  let variantStyles = '';
  if (variant === 'primary') {
    variantStyles = 'bg-blue-600 hover:bg-blue-700 text-white shadow-[0_0_15px_rgba(59,130,246,0.5)]';
  } else if (variant === 'secondary') {
    variantStyles = 'bg-[rgba(255,255,255,0.1)] hover:bg-[rgba(255,255,255,0.2)] text-white';
  } else if (variant === 'danger') {
    variantStyles = 'bg-red-600 hover:bg-red-700 text-white shadow-[0_0_15px_rgba(239,68,68,0.5)]';
  } else if (variant === 'ghost') {
    variantStyles = 'bg-transparent hover:bg-[rgba(255,255,255,0.05)] text-gray-300 hover:text-white';
  }

  const sizes = {
    sm: 'px-3 py-1.5 text-sm',
    md: 'px-4 py-2',
    lg: 'px-6 py-3 text-lg'
  };

  return (
    <button 
      className={`${baseStyles} ${variantStyles} ${sizes[size]} ${isLoading ? 'opacity-70 cursor-not-allowed' : ''} ${className}`}
      disabled={isLoading || props.disabled}
      style={{
        backgroundColor: variant === 'primary' ? 'var(--color-accent-primary)' : 
                        variant === 'danger' ? 'var(--color-danger)' : 
                        variant === 'secondary' ? 'rgba(255,255,255,0.1)' : 'transparent',
        color: 'var(--color-text-primary)',
        border: 'none',
        borderRadius: 'var(--radius-md)',
        cursor: props.disabled || isLoading ? 'not-allowed' : 'pointer',
        boxShadow: variant === 'primary' ? 'var(--shadow-neon)' : 'none',
        padding: size === 'sm' ? '0.5rem 1rem' : size === 'lg' ? '1rem 2rem' : '0.75rem 1.5rem',
        fontWeight: 500,
        transition: 'all var(--transition-fast)'
      }}
      {...props}
    >
      {isLoading ? <span style={{marginRight: '8px'}}>⌛</span> : null}
      {children}
    </button>
  );
}
