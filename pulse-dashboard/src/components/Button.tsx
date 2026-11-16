import React from 'react';
import { Link } from 'react-router-dom';
import { LoaderCircle, type LucideIcon } from 'lucide-react';
import styles from './Button.module.css';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md';
  isLoading?: boolean;
  /** Leading icon; decorative, the text label carries the meaning. */
  icon?: LucideIcon;
}

export default function Button({
  children,
  variant = 'primary',
  size = 'md',
  isLoading = false,
  icon: Icon,
  className = '',
  disabled,
  ...props
}: ButtonProps) {
  return (
    <button
      className={`${styles.button} ${styles[variant]} ${styles[size]} ${className}`}
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
      {...props}
    >
      {isLoading
        ? <LoaderCircle className={styles.spinner} size={16} aria-hidden />
        : Icon && <Icon size={16} strokeWidth={1.75} aria-hidden />}
      {children}
    </button>
  );
}

/** A link that looks like a button, for navigation actions. */
export function ButtonLink({ to, variant = 'secondary', size = 'md', icon: Icon, children }: {
  to: string;
  variant?: 'primary' | 'secondary' | 'ghost';
  size?: 'sm' | 'md';
  icon?: LucideIcon;
  children: React.ReactNode;
}) {
  return (
    <Link to={to} className={`${styles.button} ${styles[variant]} ${styles[size]} ${styles.link}`}>
      {Icon && <Icon size={16} strokeWidth={1.75} aria-hidden />}
      {children}
    </Link>
  );
}
