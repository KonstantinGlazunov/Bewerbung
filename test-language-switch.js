/**
 * Автоматический тест сохранения данных при смене языка
 * 
 * Инструкция:
 * 1. Откройте http://localhost:8080 в браузере
 * 2. Откройте консоль разработчика (F12)
 * 3. Скопируйте и вставьте этот скрипт в консоль
 * 4. Нажмите Enter для запуска теста
 */

(async function testLanguageSwitch() {
    console.log('=== Начало автоматического теста сохранения данных при смене языка ===\n');
    
    // Получаем элементы
    const biographyTextarea = document.getElementById('biographyTextarea');
    const jobPostingTextarea = document.getElementById('jobPostingTextarea');
    const wishesTextarea = document.getElementById('wishesTextarea');
    const languageSelector = document.getElementById('languageSelector');
    
    if (!biographyTextarea || !jobPostingTextarea || !languageSelector) {
        console.error('❌ Ошибка: Элементы не найдены. Убедитесь, что вы находитесь на странице приложения.');
        return;
    }
    
    // Тестовые данные пользователя (уникальные, чтобы отличить от sample данных)
    const userBiography = `МОЯ ПЕРСОНАЛЬНАЯ БИОГРАФИЯ ДЛЯ ТЕСТА ${Date.now()}

Имя: Тестовый Пользователь
Опыт работы: 5 лет разработки
Навыки: Java, JavaScript, Spring Boot
Образование: Высшее техническое
Дополнительная информация: Это тестовые данные для проверки сохранения при смене языка`;

    const userJobPosting = `МОЯ ПЕРСОНАЛЬНАЯ ВАКАНСИЯ ДЛЯ ТЕСТА ${Date.now()}

Позиция: Senior Java Developer
Компания: Test Corporation
Требования: Java, Spring, JavaScript
Опыт: от 5 лет
Локация: Удаленно`;

    const userWishes = `Мои личные пожелания для теста ${Date.now()}
Добавить информацию о моем опыте работы с микросервисами`;

    // Сохраняем исходные значения
    const originalBiography = biographyTextarea.value;
    const originalJobPosting = jobPostingTextarea.value;
    const originalWishes = wishesTextarea ? wishesTextarea.value : '';
    
    console.log('📝 Шаг 1: Ввод тестовых данных пользователя...');
    
    // Вводим тестовые данные
    biographyTextarea.value = userBiography;
    jobPostingTextarea.value = userJobPosting;
    if (wishesTextarea) {
        wishesTextarea.value = userWishes;
    }
    
    // Триггерим событие input для сохранения в некоторых фреймворках
    biographyTextarea.dispatchEvent(new Event('input', { bubbles: true }));
    jobPostingTextarea.dispatchEvent(new Event('input', { bubbles: true }));
    if (wishesTextarea) {
        wishesTextarea.dispatchEvent(new Event('input', { bubbles: true }));
    }
    
    console.log('✓ Тестовые данные введены');
    console.log(`  - Биография: ${userBiography.substring(0, 50)}...`);
    console.log(`  - Вакансия: ${userJobPosting.substring(0, 50)}...`);
    console.log(`  - Пожелания: ${userWishes.substring(0, 50)}...\n`);
    
    // Ждем немного для обработки
    await new Promise(resolve => setTimeout(resolve, 500));
    
    // Тестируем смену языков
    const languages = ['de', 'ru', 'en', 'de', 'ru', 'en'];
    let allTestsPassed = true;
    let testNumber = 0;
    
    for (let i = 0; i < languages.length; i++) {
        testNumber++;
        const lang = languages[i];
        const langName = { 'de': 'Немецкий', 'ru': 'Русский', 'en': 'Английский' }[lang];
        
        console.log(`--- Тест ${testNumber}: Смена языка на ${langName.toUpperCase()} (${lang}) ---`);
        
        // Сохраняем значения перед сменой языка
        const beforeBiography = biographyTextarea.value;
        const beforeJobPosting = jobPostingTextarea.value;
        const beforeWishes = wishesTextarea ? wishesTextarea.value : '';
        
        // Меняем язык
        languageSelector.value = lang;
        const changeEvent = new Event('change', { bubbles: true });
        languageSelector.dispatchEvent(changeEvent);
        
        // Ждем загрузки sample данных и обработки события
        await new Promise(resolve => setTimeout(resolve, 1500));
        
        // Проверяем, что данные не изменились
        const afterBiography = biographyTextarea.value;
        const afterJobPosting = jobPostingTextarea.value;
        const afterWishes = wishesTextarea ? wishesTextarea.value : '';
        
        const biographyOk = afterBiography === userBiography;
        const jobPostingOk = afterJobPosting === userJobPosting;
        const wishesOk = afterWishes === userWishes;
        
        if (biographyOk && jobPostingOk && wishesOk) {
            console.log(`✓ Тест ${testNumber} ПРОЙДЕН: все данные сохранены`);
        } else {
            console.error(`✗ Тест ${testNumber} ПРОВАЛЕН:`);
            if (!biographyOk) {
                console.error(`  ❌ Биография изменилась!`);
                console.error(`     Ожидалось: ${userBiography.substring(0, 100)}...`);
                console.error(`     Получено: ${afterBiography.substring(0, 100)}...`);
            }
            if (!jobPostingOk) {
                console.error(`  ❌ Вакансия изменилась!`);
                console.error(`     Ожидалось: ${userJobPosting.substring(0, 100)}...`);
                console.error(`     Получено: ${afterJobPosting.substring(0, 100)}...`);
            }
            if (!wishesOk) {
                console.error(`  ❌ Пожелания изменились!`);
                console.error(`     Ожидалось: ${userWishes.substring(0, 100)}...`);
                console.error(`     Получено: ${afterWishes.substring(0, 100)}...`);
            }
            allTestsPassed = false;
        }
        console.log('');
    }
    
    console.log('=== Итоговые результаты теста ===\n');
    if (allTestsPassed) {
        console.log('✅ ВСЕ ТЕСТЫ ПРОЙДЕНЫ УСПЕШНО!');
        console.log('✓ Данные пользователя сохраняются при смене языка');
        console.log('✓ Sample данные не перезаписывают пользовательские данные\n');
    } else {
        console.error('❌ НЕКОТОРЫЕ ТЕСТЫ ПРОВАЛЕНЫ!');
        console.error('✗ Данные пользователя НЕ сохраняются при смене языка');
        console.error('✗ Sample данные перезаписывают пользовательские данные\n');
    }
    
    // Восстанавливаем исходные значения
    console.log('🔄 Восстановление исходных значений...');
    biographyTextarea.value = originalBiography;
    jobPostingTextarea.value = originalJobPosting;
    if (wishesTextarea) {
        wishesTextarea.value = originalWishes;
    }
    console.log('✓ Исходные значения восстановлены\n');
    
    console.log('=== Тест завершен ===');
    
    return allTestsPassed;
})();

