import React, { createContext, useContext, useState, useEffect } from 'react';
import { 
  onIdTokenChanged, 
  signInWithEmailAndPassword, 
  createUserWithEmailAndPassword, 
  signInWithPopup, 
  GoogleAuthProvider, 
  signOut, 
  sendPasswordResetEmail 
} from 'firebase/auth';
import { auth } from './firebase';

// Create Session context
const SessionContext = createContext(null);

/**
 * SessionProvider component to wrap the React application and manage
 * the Firebase Auth session state securely.
 */
export const SessionProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // onIdTokenChanged fires automatically whenever:
    // 1. A user signs in or out
    // 2. The ID token is automatically refreshed by the SDK (occurs hourly)
    // 3. The current user's token is manually refreshed
    const unsubscribe = onIdTokenChanged(auth, async (firebaseUser) => {
      setLoading(true);
      if (firebaseUser) {
        try {
          const idToken = await firebaseUser.getIdToken();
          setUser(firebaseUser);
          setToken(idToken);
          // Sync token securely for Axios REST API interceptor use
          localStorage.setItem('token', idToken);
        } catch (error) {
          console.error('Error fetching Firebase ID token:', error);
          setUser(null);
          setToken(null);
          localStorage.removeItem('token');
        }
      } else {
        setUser(null);
        setToken(null);
        // Clear token on sign-out
        localStorage.removeItem('token');
      }
      setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  // Email/Password sign-in
  const login = (email, password) => {
    return signInWithEmailAndPassword(auth, email, password);
  };

  // Email/Password sign-up
  const register = (email, password) => {
    return createUserWithEmailAndPassword(auth, email, password);
  };

  // Google OAuth sign-in
  const loginWithGoogle = () => {
    const provider = new GoogleAuthProvider();
    return signInWithPopup(auth, provider);
  };

  // Sign out
  const logout = () => {
    return signOut(auth);
  };

  // Password reset email dispatch
  const resetPassword = (email) => {
    return sendPasswordResetEmail(auth, email);
  };

  const value = {
    user,
    token,
    loading,
    login,
    register,
    loginWithGoogle,
    logout,
    resetPassword,
  };

  return (
    <SessionContext.Provider value={value}>
      {children}
    </SessionContext.Provider>
  );
};

/**
 * Custom hook to consume the Firebase Session Context.
 */
export const useSession = () => {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error('useSession must be used within a SessionProvider');
  }
  return context;
};
