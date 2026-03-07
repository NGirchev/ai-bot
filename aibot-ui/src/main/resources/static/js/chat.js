(function() {
  const API = '/api/v1/session';

  const el = {
    sessionList: null,
    newChatBtn: null,
    deleteChatBtn: null,
    chatTitle: null,
    messages: null,
    form: null,
    input: null,
    sendBtn: null,
    userEmail: null,
    logoutBtn: null,
  };

  let sessions = []; // {sessionId, name, createdAt}
  let currentSessionId = null;
  let isSending = false;
  let userEmail = null;
  let renderScheduled = false; // Флаг для батчинга обновлений DOM

  document.addEventListener('DOMContentLoaded', () => {
    el.sessionList = document.getElementById('sessionList');
    el.newChatBtn = document.getElementById('newChatBtn');
    el.deleteChatBtn = document.getElementById('deleteChatBtn');
    el.chatTitle = document.getElementById('chatTitle');
    el.messages = document.getElementById('messages');
    el.form = document.getElementById('messageForm');
    el.input = document.getElementById('messageInput');
    el.sendBtn = document.getElementById('sendBtn');
    el.userEmail = document.getElementById('userEmail');
    el.logoutBtn = document.getElementById('logoutBtn');

    if (el.newChatBtn) {
      el.newChatBtn.addEventListener('click', startNewChat);
    }
    if (el.deleteChatBtn) {
      el.deleteChatBtn.addEventListener('click', onDeleteChat);
    }
    if (el.logoutBtn) {
      el.logoutBtn.addEventListener('click', onLogout);
    }

    if (el.form) {
      el.form.addEventListener('submit', (e) => {
        e.preventDefault();
        onSend();
      });
    }

    if (el.input) {
      el.input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
          if (e.shiftKey) {
            return; // allow newline
          }
          e.preventDefault();
          onSend();
        }
      });

      el.input.addEventListener('input', () => {
        autoResizeTextarea(el.input);
        updateSendButtonState();
      });
    }

    window.addEventListener('hashchange', handleHashChange);

    // Инициализация с обработкой ошибок
    try {
      init();
    } catch (e) {
      console.error('Error during initialization:', e);
      // Гарантируем, что форма видна даже при ошибке
      if (el.form && el.form.style) {
        el.form.style.display = '';
      }
    }
  });

  async function init() {
    // Проверяем авторизацию и получаем email пользователя
    try {
      const userInfo = await getJSON('/api/v1/ui/me');
      userEmail = userInfo.email;
      if (el.userEmail) {
        el.userEmail.textContent = userEmail;
      }
    } catch (e) {
      // Если не авторизован, перенаправляем на страницу логина
      window.location.href = '/login';
      return;
    }
    
    await loadSessions();
    const fromHash = getHashSessionId();
    if (fromHash) {
      selectSession(fromHash, {updateHash: false});
    } else {
      setUIForNewChat();
    }
  }

  function autoResizeTextarea(ta) {
    ta.style.height = 'auto';
    ta.style.height = Math.min(200, ta.scrollHeight) + 'px';
  }

  function getHashSessionId() {
    const h = (window.location.hash || '').replace('#','').trim();
    return h || null;
  }

  function setHashSessionId(id) {
    if (!id) {
      history.replaceState(null, '', window.location.pathname);
    } else {
      if (getHashSessionId() !== id) {
        window.location.hash = id;
      }
    }
  }

  async function loadSessions() {
    try {
      // Email теперь берется из сессии на сервере, не нужно передавать в запросе
      const list = await getJSON(`${API}`);
      // sort by createdAt desc
      list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
      sessions = list;
      renderSessions();
      // Keep selection highlight if still present
      if (currentSessionId && !sessions.find(s => s.sessionId === currentSessionId)) {
        // current was removed
        setUIForNewChat();
      } else {
        highlightSelected(currentSessionId);
      }
    } catch (e) {
      console.error('Error loading sessions:', e);
      // Если статус 401, сразу делаем редирект
      if (e.status === 401 || e.redirect) {
        const redirectUrl = e.redirect || '/login';
        console.log('Unauthorized - redirecting to:', redirectUrl);
        window.location.href = redirectUrl;
        return;
      }
      if (!handleAuthError(e)) {
        alert('Failed to load chats');
      }
    }
  }

  function renderSessions() {
    el.sessionList.innerHTML = '';
    sessions.forEach(s => {
      const li = document.createElement('li');
      li.dataset.id = s.sessionId;
      li.addEventListener('click', () => selectSession(s.sessionId));

      const name = document.createElement('div');
      name.className = 'name';
      name.textContent = s.name || 'Untitled';

      const date = document.createElement('div');
      date.className = 'date';
      try {
        const d = new Date(s.createdAt);
        date.textContent = d.toLocaleString();
      } catch (_) { date.textContent = ''; }

      li.appendChild(name);
      li.appendChild(date);
      el.sessionList.appendChild(li);
    });
  }

  function highlightSelected(sessionId) {
    const items = el.sessionList.querySelectorAll('li');
    items.forEach(li => {
      li.classList.toggle('selected', !!sessionId && li.dataset.id === sessionId);
    });
  }

  function startNewChat() {
    currentSessionId = null;
    setHashSessionId(null);
    setDeleteButtonDisabled(true);
    setUIForNewChat();
  }

  function setUIForNewChat() {
    if (el.chatTitle) {
      el.chatTitle.textContent = 'New chat';
    }
    setDeleteButtonDisabled(true);
    if (el.messages) {
      el.messages.innerHTML = '';
    }
    if (el.input) {
      el.input.value = '';
      // Гарантируем, что поле ввода разблокировано
      setSendingState(false);
      el.input.focus();
    }
    updateSendButtonState();
    highlightSelected(null);
  }

  async function selectSession(sessionId, opts = {updateHash: true}) {
    if (!sessionId) return;
    currentSessionId = sessionId;
    if (opts.updateHash) setHashSessionId(sessionId);

    const s = sessions.find(x => x.sessionId === sessionId);
    el.chatTitle.textContent = s ? (s.name || 'Chat') : 'Chat';
    setDeleteButtonDisabled(false);
    highlightSelected(sessionId);

    await loadHistory(sessionId);
  }

  async function loadHistory(sessionId) {
    try {
      // Email теперь берется из сессии на сервере, не нужно передавать в запросе
      const data = await getJSON(`${API}/${sessionId}/messages`);
      const items = (data && Array.isArray(data.messages)) ? data.messages : [];
      // filter out SYSTEM
      const filtered = items.filter(m => (m.role || '').toUpperCase() !== 'SYSTEM');
      renderMessages(filtered);
      scrollToBottom();
    } catch (e) {
      console.error('Error loading history:', e);
      // Если статус 401, сразу делаем редирект
      if (e.status === 401 || e.redirect) {
        const redirectUrl = e.redirect || '/login';
        console.log('Unauthorized - redirecting to:', redirectUrl);
        window.location.href = redirectUrl;
        return;
      }
      if (!handleAuthError(e)) {
        alert('Failed to load chat history');
      }
    }
  }

  function renderMessages(messages) {
    el.messages.innerHTML = '';
    messages.forEach(m => appendMessage(m.role, m.content));
  }

  function appendMessage(role, content) {
    const div = document.createElement('div');
    const r = (role || '').toUpperCase();
    div.className = 'message ' + (r === 'USER' ? 'user' : 'assistant');
    if (r === 'ASSISTANT') {
      renderAssistantContent(div, content);
    } else {
      div.textContent = content || '';
    }
    el.messages.appendChild(div);
    return div;
  }

  function appendStreamingMessage(role) {
    const div = document.createElement('div');
    const r = (role || '').toUpperCase();
    div.className = 'message ' + (r === 'USER' ? 'user' : 'assistant');
    if (r === 'ASSISTANT') {
      // Создаем контейнер для постепенного добавления контента
      const contentContainer = document.createElement('div');
      // Создаем один textNode для накопления текста - используем nodeValue для обновления
      const textNode = document.createTextNode('');
      contentContainer.appendChild(textNode);
      div.appendChild(contentContainer);
      div._contentContainer = contentContainer;
      div._textNode = textNode; // Сохраняем ссылку на textNode для обновления
      div._contentBuffer = '';
      div._renderTimeout = null; // Для периодической перерисовки think-блоков
    } else {
      div.textContent = '';
    }
    el.messages.appendChild(div);
    return div;
  }

  function appendToStreamingMessage(messageDiv, text) {
    // const timestamp = new Date().toISOString();
    // console.log(`[${timestamp}] Message received:`, JSON.stringify(text));
    
    if (!messageDiv || !messageDiv._contentContainer) {
      console.error('appendToStreamingMessage: invalid messageDiv', messageDiv);
      return;
    }
    
    if (!messageDiv._contentBuffer) {
      messageDiv._contentBuffer = '';
    }
    
    // Накапливаем текст в буфере
    messageDiv._contentBuffer += text;
    
    // Обновляем один textNode вместо создания новых - это предотвращает переносы строк
    // Используем nodeValue вместо textContent для более эффективного обновления
    if (messageDiv._textNode) {
      // Проверяем, что это действительно textNode (nodeType === 3)
      if (messageDiv._textNode.nodeType === 3) {
        messageDiv._textNode.nodeValue = messageDiv._contentBuffer;
      } else {
        // Если это не textNode, обновляем textContent
        messageDiv._textNode.textContent = messageDiv._contentBuffer;
      }
    } else {
      // Fallback: если textNode не создан, создаем его
      messageDiv._textNode = document.createTextNode(messageDiv._contentBuffer);
      messageDiv._contentContainer.appendChild(messageDiv._textNode);
    }
    
    // Прокручиваем вниз при каждом обновлении
    scrollToBottom();
    
    // Периодически перерисовываем для обработки think-блоков (если они есть)
    // Но не при каждом символе - это слишком дорого
    if (!messageDiv._renderTimeout) {
      messageDiv._renderTimeout = setTimeout(() => {
        messageDiv._renderTimeout = null;
        // Перерисовываем для обработки think-блоков, если они появились
        const currentContent = messageDiv._contentBuffer;
        if (currentContent.includes('<think>')) {
          renderAssistantContent(messageDiv._contentContainer, currentContent);
          // После перерисовки нужно обновить ссылку на textNode
          // Но renderAssistantContent создает новые узлы, поэтому textNode нужно будет пересоздать
          messageDiv._textNode = null;
        }
      }, 100); // Перерисовываем раз в 100мс, если есть think-блоки
    }
  }

  function renderAssistantContent(container, content) {
    // Очищаем контейнер перед перерисовкой
    container.innerHTML = '';
    
    if (!content) {
      return;
    }

    const regex = /<think>([\s\S]*?)<\/think>/gi;
    let lastIndex = 0;
    let match;

    while ((match = regex.exec(content)) !== null) {
      const textBefore = content.slice(lastIndex, match.index);
      if (textBefore) {
        container.appendChild(document.createTextNode(textBefore));
      }

      const details = document.createElement('details');
      details.className = 'think-spoiler';
      const summary = document.createElement('summary');
      summary.textContent = 'Thoughts';
      const inner = document.createElement('div');
      inner.textContent = match[1].trim();

      details.appendChild(summary);
      details.appendChild(inner);
      container.appendChild(details);

      lastIndex = regex.lastIndex;
    }

    const tail = content.slice(lastIndex);
    if (tail) {
      container.appendChild(document.createTextNode(tail));
    }
  }

  function scrollToBottom() {
    el.messages.scrollTop = el.messages.scrollHeight;
  }

  function updateSendButtonState() {
    if (!el.sendBtn) return;
    const hasText = (el.input.value || '').trim().length > 0;
    el.sendBtn.disabled = isSending || !hasText;
  }

  function setDeleteButtonDisabled(disabled) {
    if (!el.deleteChatBtn) return;
    el.deleteChatBtn.disabled = disabled;
    if (disabled) {
      el.deleteChatBtn.classList.add('is-disabled');
      el.deleteChatBtn.setAttribute('aria-disabled', 'true');
    } else {
      el.deleteChatBtn.classList.remove('is-disabled');
      el.deleteChatBtn.removeAttribute('aria-disabled');
    }
  }

  function setSendingState(sending) {
    isSending = sending;
    if (el.input) {
      el.input.disabled = sending;
    }
    if (el.sendBtn) {
      if (sending) {
        el.sendBtn.disabled = true;
      } else {
        updateSendButtonState();
      }
    }
  }

  async function onSend() {
    const text = (el.input.value || '').trim();
    if (!text) return;

    if (!currentSessionId) {
      // New chat flow: используем стриминг
      setSendingState(true);
      appendMessage('USER', text);
      const assistantMessageDiv = appendStreamingMessage('ASSISTANT');
      scrollToBottom();
      
      try {
        await streamMessage(`${API}/stream`, { message: text }, assistantMessageDiv, (sessionId) => {
          currentSessionId = sessionId;
          setHashSessionId(currentSessionId);
          loadSessions();
        });
        el.input.value = '';
        autoResizeTextarea(el.input);
        updateSendButtonState();
      } catch (e) {
        console.error('Error sending message (new chat):', e);
        // Удаляем сообщения пользователя и ассистента при ошибке
        const messages = el.messages.querySelectorAll('.message');
        if (messages.length >= 2) {
          messages[messages.length - 1].remove(); // assistant
          messages[messages.length - 2].remove(); // user
        } else if (messages.length >= 1) {
          messages[messages.length - 1].remove();
        }
        // Разблокируем поле ввода перед обработкой ошибки
        setSendingState(false);
        
        // Если статус 401, сразу делаем редирект
        if (e.status === 401 || e.redirect) {
          const redirectUrl = e.redirect || '/login';
          console.log('Unauthorized - redirecting to:', redirectUrl);
          window.location.href = redirectUrl;
          return;
        }
        // Пытаемся обработать как ошибку авторизации через handleAuthError
        if (handleAuthError(e)) {
          return; // Редирект произошел, выходим
        }
        // Если это не ошибка авторизации, показываем сообщение
        alert('Failed to send message: ' + (e.message || 'Unknown error'));
      } finally {
        // Дополнительная проверка - разблокируем поле ввода на всякий случай
        if (isSending) {
          setSendingState(false);
        }
      }
    } else {
      // Existing chat: используем стриминг
      appendMessage('USER', text);
      scrollToBottom();
      const assistantMessageDiv = appendStreamingMessage('ASSISTANT');
      scrollToBottom();
      setSendingState(true);
      
      try {
        await streamMessage(`${API}/${currentSessionId}/stream`, { message: text }, assistantMessageDiv);
        el.input.value = '';
        autoResizeTextarea(el.input);
        updateSendButtonState();
      } catch (e) {
        console.error('Error sending message (existing chat):', e);
        // Удаляем сообщения пользователя и ассистента при ошибке
        const messages = el.messages.querySelectorAll('.message');
        if (messages.length >= 2) {
          messages[messages.length - 1].remove(); // assistant
          messages[messages.length - 2].remove(); // user
        } else if (messages.length >= 1) {
          messages[messages.length - 1].remove();
        }
        // Разблокируем поле ввода перед обработкой ошибки
        setSendingState(false);
        
        // Если статус 401, сразу делаем редирект
        if (e.status === 401 || e.redirect) {
          const redirectUrl = e.redirect || '/login';
          console.log('Unauthorized - redirecting to:', redirectUrl);
          window.location.href = redirectUrl;
          return;
        }
        // Пытаемся обработать как ошибку авторизации через handleAuthError
        if (handleAuthError(e)) {
          return; // Редирект произошел, выходим
        }
        // Если это не ошибка авторизации, показываем сообщение
        alert('Failed to send message: ' + (e.message || 'Unknown error'));
      } finally {
        // Дополнительная проверка - разблокируем поле ввода на всякий случай
        if (isSending) {
          setSendingState(false);
        }
      }
    }
  }

  async function streamMessage(url, body, messageDiv, onSessionCreated = null) {
    return new Promise((resolve, reject) => {
      fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream'
        },
        body: JSON.stringify(body),
        credentials: 'include'
      })
      .then(response => {
        if (!response.ok) {
          // Пытаемся обработать ошибку
          return response.text().then(text => {
            const error = new Error(`HTTP ${response.status}: ${text}`);
            error.status = response.status;
            if (response.status === 401) {
              error.redirect = '/login';
            }
            throw error;
          });
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let sessionId = null;
        let currentEventType = null;
        let currentData = null;

        function readChunk() {
          reader.read().then(({ done, value }) => {
            if (done) {
              // Обрабатываем последние данные, если есть
              if (currentData !== null && currentEventType === 'metadata') {
                try {
                  const json = JSON.parse(currentData);
                  if (json.sessionId) {
                    sessionId = json.sessionId;
                    if (onSessionCreated) {
                      onSessionCreated(sessionId);
                    }
                  }
                } catch (e) {
                  console.warn('Failed to parse metadata:', e);
                }
              }
              setSendingState(false);
              resolve(sessionId);
              return;
            }

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || ''; // Оставляем неполную строку в буфере

            for (const line of lines) {
              // НЕ используем trim() для всей строки - это удалит пробелы в данных!
              // Проверяем только на пустую строку (без пробелов)
              if (line === '') {
                // Пустая строка означает конец события - обрабатываем накопленные данные
                // Обрабатываем только если есть данные (не пустая строка)
                if (currentData !== null && currentData !== undefined && currentData !== '') {
                  if (currentEventType === 'metadata') {
                    // Обрабатываем метаданные (sessionId)
                    try {
                      const json = JSON.parse(currentData);
                      if (json.sessionId) {
                        sessionId = json.sessionId;
                        if (onSessionCreated) {
                          onSessionCreated(sessionId);
                        }
                      }
                    } catch (e) {
                      console.warn('Failed to parse metadata:', e);
                    }
                  } else {
                    // Обычный контент сообщения (без event type или с пустым event type)
                    if (currentData) {
                      appendToStreamingMessage(messageDiv, currentData);
                    }
                  }
                  currentData = null;
                  currentEventType = null;
                }
                continue;
              }
              
              // Парсим SSE формат
              // SSE формат: "event:тип" или "data:данные" (может быть с пробелом или без)
              // Используем trim() только для проверки префикса, но не для данных
              const trimmedForCheck = line.trim();
              if (trimmedForCheck.startsWith('event:')) {
                // Извлекаем тип события (может быть "event:тип" или "event: тип")
                const eventPart = trimmedForCheck.substring(6); // После "event:"
                currentEventType = eventPart.trim();
              } else if (trimmedForCheck.startsWith('data:')) {
                // Извлекаем данные (может быть "data:данные" или "data: данные")
                // ВАЖНО: используем оригинальную строку line, а не trimmedForCheck!
                // Это сохраняет пробелы в начале данных
                const dataPart = line.substring(5); // После "data:"
                // НЕ используем trim() - сохраняем все пробелы и символы как есть
                const data = dataPart;
                
                // Игнорируем пустые data: строки полностью - они не несут информации
                if (data === '') {
                  continue;
                }
                
                // Накапливаем непустые данные
                // Для стриминга посимвольно просто добавляем символы друг за другом
                if (currentData === null || currentData === undefined) {
                  currentData = data;
                } else {
                  // Просто добавляем данные без переноса строки (символы идут подряд)
                  currentData += data;
                }
              }
            }

            readChunk();
          }).catch(err => {
            setSendingState(false);
            reject(err);
          });
        }

        readChunk();
      })
      .catch(err => {
        setSendingState(false);
        reject(err);
      });
    });
  }

  async function onDeleteChat() {
    if (!currentSessionId) return;
    const ok = window.confirm('Delete current chat?');
    if (!ok) return;
    try {
      // Email теперь берется из сессии на сервере, не нужно передавать в запросе
      await del(`${API}/${currentSessionId}`);
      currentSessionId = null;
      setHashSessionId(null);
      await loadSessions();
      setUIForNewChat();
    } catch (e) {
      console.error('Error deleting chat:', e);
      // Если статус 401, сразу делаем редирект
      if (e.status === 401 || e.redirect) {
        const redirectUrl = e.redirect || '/login';
        console.log('Unauthorized - redirecting to:', redirectUrl);
        window.location.href = redirectUrl;
        return;
      }
      if (!handleAuthError(e)) {
        alert('Failed to delete chat');
      }
    }
  }

  function handleHashChange() {
    const id = getHashSessionId();
    if (id && id !== currentSessionId) {
      selectSession(id, {updateHash: false});
    } else if (!id && currentSessionId) {
      startNewChat();
    }
  }

  async function onLogout() {
    try {
      await postJSON('/api/v1/ui/logout', {});
      window.location.href = '/login';
    } catch (e) {
      console.error(e);
      // В любом случае перенаправляем на логин
      window.location.href = '/login';
    }
  }

  // Helper для обработки ошибок авторизации
  function handleAuthError(e) {
    // Проверяем статус код 401 или наличие поля redirect
    const status = e.status;
    const hasRedirect = e.redirect;
    const message = e.message || '';
    const isUnauthorized = status === 401 || 
                          message.includes('401') || 
                          message.includes('Unauthorized') ||
                          hasRedirect;
    
    if (isUnauthorized) {
      const redirectUrl = hasRedirect ? e.redirect : '/login';
      console.log('Unauthorized error detected. Status:', status, 'Redirect:', redirectUrl, 'Error:', e);
      // Немедленно делаем редирект
      window.location.href = redirectUrl;
      return true;
    }
    return false;
  }

  // HTTP helpers
  async function getJSON(url) {
    const resp = await fetch(url, { 
      headers: { 'Accept': 'application/json' },
      credentials: 'include' // Важно для отправки cookies (сессии)
    });
    if (!resp.ok) {
      await ensureOk(resp.clone()); // Используем clone, чтобы не "съесть" тело
    }
    return resp.json();
  }

  async function postJSON(url, body) {
    const resp = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify(body || {}),
      credentials: 'include' // Важно для отправки cookies (сессии)
    });
    if (!resp.ok) {
      await ensureOk(resp.clone()); // Используем clone, чтобы не "съесть" тело
    }
    return resp.json();
  }

  async function del(url) {
    const resp = await fetch(url, { 
      method: 'DELETE',
      headers: { 'Accept': 'application/json' },
      credentials: 'include' // Важно для отправки cookies (сессии)
    });
    if (!resp.ok) {
      await ensureOk(resp.clone()); // Используем clone, чтобы не "съесть" тело
    }
    return true;
  }

  async function ensureOk(resp) {
    if (!resp.ok) {
      let msg = `HTTP ${resp.status}`;
      let redirectUrl = null;
      const status = resp.status;
      
      // Пытаемся извлечь JSON из ответа
      try {
        const contentType = resp.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
          const err = await resp.json();
          if (err && err.message) msg += `: ${err.message}`;
          if (err && err.redirect) redirectUrl = err.redirect;
        } else {
          // Если не JSON, пробуем прочитать как текст
          const text = await resp.text();
          if (text) msg += `: ${text}`;
        }
      } catch(e) {
        console.warn('Failed to parse error response:', e);
        // Если не удалось распарсить, просто используем статус код
      }
      
      const error = new Error(msg);
      error.status = status; // Сохраняем статус код для проверки
      if (redirectUrl) {
        error.redirect = redirectUrl;
      }
      // Если статус 401 (Unauthorized), всегда устанавливаем redirect
      if (status === 401) {
        error.redirect = redirectUrl || '/login';
      }
      throw error;
    }
  }
})();
