#!/bin/bash

# Create a backup
cp app/src/main/java/com/example/baseui1/tasks/TaskManager.kt app/src/main/java/com/example/baseui1/tasks/TaskManager.kt.corrupted

# Fix all corrupted when statements
sed -i '/For AAT tasks, convert and use AAT display system/,/^[[:space:]]*}/c\
                    else -> TaskType.DHT // Default fallback\
                }' app/src/main/java/com/example/baseui1/tasks/TaskManager.kt

# If that didn't work, try a more targeted approach
# Fix specific patterns manually
sed -i 's/TaskType\.AAT$/TaskType.AAT/' app/src/main/java/com/example/baseui1/tasks/TaskManager.kt

echo "Fixed TaskManager.kt"
