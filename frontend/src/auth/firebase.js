import { initializeApp } from 'firebase/app';
import { getAuth, connectAuthEmulator } from 'firebase/auth';

// Firebase configuration using Vite environment variables
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || 'dummy-api-key-for-init',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);

// Connect to Firebase Auth Emulator if configured for local development
if (import.meta.env.DEV && import.meta.env.VITE_USE_FIREBASE_EMULATOR === 'true') {
  const host = import.meta.env.VITE_FIREBASE_EMULATOR_HOST || '127.0.0.1';
  const port = parseInt(import.meta.env.VITE_FIREBASE_AUTH_EMULATOR_PORT || '9099', 10);
  
  try {
    connectAuthEmulator(auth, `http://${host}:${port}`);
    console.log(`Firebase Auth Emulator connected successfully at http://${host}:${port}`);
  } catch (error) {
    console.warn('Failed to connect to Firebase Auth Emulator:', error);
  }
}

export { app, auth };
