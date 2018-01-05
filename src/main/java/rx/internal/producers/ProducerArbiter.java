/**
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package rx.internal.producers;

import rx.*;

/**
 * Producer that allows changing an underlying producer atomically and correctly resume with the accumulated
 * requests.
 */
//Produce 仲裁者 方便切换Producer 解决的问题是中途切换了Producer避免Producer重复发送已经发送的数据
//    主要解决的问题：切换数据源时 新的数据源 只发送当前请求但是未发送的数据（已发送多少数据由使用者自己调用produced进行更新
//    无法保证的是：对下游onXXX的顺序调用，因为实际发射过程中可能存在异步的发射数据的可能
public final class ProducerArbiter implements Producer {
    long requested;/*更新这个值的时候未加锁*/
    Producer currentProducer;

    boolean emitting;
    long missedRequested;/*更新这个值的时候 lock this*/
    long missedProduced;/*也lock this 避免并发更新*/
    Producer missedProducer;

    static final Producer NULL_PRODUCER = new Producer() {
        @Override
        public void request(long n) {
            // deliberately ignored
        }
    };

    @Override
    public void request(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("n >= 0 required");
        }
        if (n == 0) {
            return;
        }
//        如果正在发射数据 也不会更新requested值 只是将missedRequested值进行递增
        synchronized (this) {
            if (emitting) {
                missedRequested += n;
                return;
            }
            emitting = true;
        }
        boolean skipFinal = false;
        try {
//            确保数据不超过Long Max
            long r = requested;
            long u = r + n;
            if (u < 0) {
                u = Long.MAX_VALUE;
            }
            requested = u;

            Producer p = currentProducer;
            if (p != null) {
                p.request(n);
            }

            emitLoop();
            skipFinal = true;
        } finally {
            if (!skipFinal) {
                synchronized (this) {
                    emitting = false;
                }
            }
        }
    }

    public void produced(long n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n > 0 required");
        }
        synchronized (this) {
            if (emitting) {
                missedProduced += n;
                return;
            }
            emitting = true;
        }

        boolean skipFinal = false;
        try {
            long r = requested;
            if (r != Long.MAX_VALUE) {
                long u = r - n;
                if (u < 0) {
                    throw new IllegalStateException("more items arrived than were requested");
                }
                requested = u;
            }
//不在发射则进入发射循环
            emitLoop();
            skipFinal = true;
        } finally {
            if (!skipFinal) {
                synchronized (this) {
                    emitting = false;
                }
            }
        }
    }
//切换Producer可能导致下游的onNext事件被并发调用
// TODO: 18-1-4 发射循环 和 新的Producer一起调用下面的生产者
    public void setProducer(Producer newProducer) {
//        如果正在发射 设置新的Producer时并不会调用新的Producer进行数据的发射
//        只是暂存该Producer方法返回 发射循环中会检查是否有错过的Producer
        synchronized (this) {
            if (emitting) {
                missedProducer = newProducer == null ? NULL_PRODUCER : newProducer;
                return;
            }
//          不在发射则进入发射循环
            emitting = true;
        }
        boolean skipFinal = false;
        try {
            currentProducer = newProducer;
            if (newProducer != null) {
                newProducer.request(requested);
            }

            emitLoop();
            skipFinal = true;
        } finally {
            if (!skipFinal) {
                synchronized (this) {
                    emitting = false;
                }
            }
        }
    }

    // TODO: 18-1-4 会不会一直在发射循环中不退出？
//     后面设置的Producer会被错过，目前看如果一直在发射循环中不退出，后面连续设置的Producer中间的会被跳过
//    requested请求进入时会被一直累加
    public void emitLoop() {
        for (;;) {
            long localRequested;
            long localProduced;
            Producer localProducer;
//            一次发射循环获取到错过的数据之后 边将错过的数据置位0
            synchronized (this) {
                localRequested = missedRequested;
                localProduced = missedProduced;
                localProducer = missedProducer;
                if (localRequested == 0L
                        && localProduced == 0L
                        && localProducer == null) {
                    emitting = false;
                    return;
                }
                missedRequested = 0L;
                missedProduced = 0L;
                missedProducer = null;
            }

            long r = requested;
//发射循环中更新剩余的requested 根据request 和 Produced的参数进行更正
            if (r != Long.MAX_VALUE) {
                long u = r + localRequested;
                if (u < 0 || u == Long.MAX_VALUE) {
                    r = Long.MAX_VALUE;
                    requested = r;
                } else {
                    long v = u - localProduced;
                    if (v < 0) {
                        throw new IllegalStateException("more produced than requested");
                    }
                    r = v;
                    requested = v;
                }
            }
//            判断是否被更新了Producer，如果被更新了Producer则用新的Producer发射数据
//            如果没有被更新Producer则使用原先的Producer进行数据的发射
            if (localProducer != null) {
                if (localProducer == NULL_PRODUCER) {
                    currentProducer = null;
                } else {
                    currentProducer = localProducer;
                    localProducer.request(r);
                }
            } else {
                Producer p = currentProducer;
                if (p != null && localRequested != 0L) {
                    p.request(localRequested);
                }
            }
        }
    }
}