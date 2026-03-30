with open('e:/NagramX/TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace('// cell.isTop = position < spanCount;', 'cell.isTop = position < spanCount;')

with open('e:/NagramX/TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java', 'w', encoding='utf-8', newline='') as f:
    f.write(text)
