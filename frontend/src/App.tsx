import { useAuth } from './auth/AuthContext';
import LoginPage from './auth/LoginPage';
import ChatPage from './chat/ChatPage';

export default function App() {
  const { auth } = useAuth();
  return auth ? <ChatPage /> : <LoginPage />;
}
