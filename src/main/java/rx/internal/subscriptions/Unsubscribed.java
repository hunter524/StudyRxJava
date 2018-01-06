/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.internal.subscriptions;

import rx.Subscription;

/**
 * Represents an unsubscribed Subscription via a singleton; don't leak it!
 */
public enum Unsubscribed implements Subscription {
    INSTANCE;/*用一个原子变量或者用一个 final static 对象表示一种状态 是一种编码方式 避免了重用相同对象去进行状态位的设置 直接判断对象是否是特定的状态对象即可*/

    @Override
    public boolean isUnsubscribed() {
        return true;
    }

    @Override
    public void unsubscribe() {
        // deliberately ignored
    }
}