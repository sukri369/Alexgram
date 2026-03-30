with open('e:/NagramX/TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i in range(3565, 3585):
    print(f"{i}: {lines[i].strip()}")
